"""PaddleOCR PP-OCRv5 (Apache-2.0) on the CPU: detection and recognition only, no orientation or unwarping model.

The engine is loaded once per process (a few seconds); its models come from PaddleOCR's own model hub on first use.
"""

from __future__ import annotations

import logging

import numpy as np

from document_reader.port import READER_UNAVAILABLE, Box, Line, Page, ReaderError, Transcript, reading_order

OCR_VERSION = "PP-OCRv5"

log = logging.getLogger(__name__)


class PaddleReader:
    id = "paddleocr"
    generative = False

    def __init__(self) -> None:
        try:
            import paddleocr  # noqa: PLC0415 — the engine is imported only by the host that serves it
            from paddleocr import PaddleOCR  # noqa: PLC0415

            self._engine = PaddleOCR(
                ocr_version=OCR_VERSION,
                device="cpu",
                use_doc_orientation_classify=False,
                use_doc_unwarping=False,
                use_textline_orientation=False,
            )
        except Exception as failed:  # noqa: BLE001 — any failure to load is the reader being unavailable, said by code
            log.warning("paddleocr cannot load: %s", type(failed).__name__)
            raise ReaderError(READER_UNAVAILABLE) from None
        self._version = f"paddleocr-{paddleocr.__version__} {OCR_VERSION}"

    def read(self, page: Page) -> Transcript:
        image = page.image()
        bgr = np.asarray(image)[:, :, ::-1].copy()  # PaddleOCR reads arrays as OpenCV does: blue, green, red
        try:
            results = self._engine.predict(bgr)
        except Exception as failed:  # noqa: BLE001 — the engine's message can quote the page: its type name only
            log.warning("paddleocr cannot read: %s", type(failed).__name__)
            raise ReaderError(READER_UNAVAILABLE) from None
        lines = []
        for result in results:
            data = result.json
            data = data.get("res", data)
            for text, score, box in zip(data["rec_texts"], data["rec_scores"], data["rec_boxes"], strict=True):
                if text.strip():
                    left, top, right, bottom = (float(v) for v in box)
                    lines.append(
                        Line(
                            text.strip(),
                            Box.of_pixels(left, top, right, bottom, image.width, image.height),
                            float(score),
                        )
                    )
        return Transcript(lines=reading_order(lines), version=self._version)
