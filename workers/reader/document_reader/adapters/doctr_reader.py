"""docTR (Apache-2.0, Mindee) on the CPU with PyTorch: a DBNet detector and a CRNN recogniser, pretrained weights.

docTR gives a confidence per word, not per line: a line's confidence is the mean of its words'.
"""

from __future__ import annotations

import io

from document_reader.port import READER_UNAVAILABLE, Box, Line, Page, ReaderError, Transcript, reading_order

DETECTION = "db_resnet50"
RECOGNITION = "crnn_vgg16_bn"


class DoctrReader:
    id = "doctr"
    generative = False

    def __init__(self) -> None:
        try:
            import doctr  # noqa: PLC0415 — the engine is imported only by the host that serves it
            from doctr.io import DocumentFile  # noqa: PLC0415
            from doctr.models import ocr_predictor  # noqa: PLC0415

            self._predictor = ocr_predictor(det_arch=DETECTION, reco_arch=RECOGNITION, pretrained=True)
        except Exception:  # noqa: BLE001 — any failure to load is the reader being unavailable, said by code
            raise ReaderError(READER_UNAVAILABLE) from None
        self._files = DocumentFile
        self._version = f"doctr-{doctr.__version__.lstrip('v')} {DETECTION}+{RECOGNITION}"

    def read(self, page: Page) -> Transcript:
        image = page.image()
        png = io.BytesIO()
        image.save(png, format="PNG")
        try:
            exported = self._predictor(self._files.from_images(png.getvalue())).export()
        except Exception:  # noqa: BLE001 — the engine's message can quote the page; the code says enough
            raise ReaderError(READER_UNAVAILABLE) from None
        lines = []
        for found in exported["pages"]:
            for block in found["blocks"]:
                for line in block["lines"]:
                    words = [w for w in line["words"] if w["value"].strip()]
                    if not words:
                        continue
                    (left, top), (right, bottom) = line["geometry"]
                    confidence = sum(float(w["confidence"]) for w in words) / len(words)
                    text = " ".join(w["value"].strip() for w in words)
                    lines.append(Line(text, Box(float(left), float(top), float(right), float(bottom)), confidence))
        return Transcript(lines=reading_order(lines), version=self._version)
