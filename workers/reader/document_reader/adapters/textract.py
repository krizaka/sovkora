"""AWS Textract ``DetectDocumentText`` (Montréal, ``ca-central-1``): the transport is injected, the answer parsed here.

The adapter knows the response's shape, never the SDK: ``detect`` takes the page's bytes and returns Textract's JSON
answer as a dict (boto3's ``detect_document_text`` result, or a recorded one). A transport failure says what kind it is
with ``TextractUnavailable`` or ``TextractRefused``; any other exception is the reader being unavailable. Until the
account exists the contract runs on a recorded answer (``fixtures/``) and no call leaves CI.
"""

from __future__ import annotations

import io
from typing import Callable

from document_reader.port import (
    READER_REFUSED,
    READER_UNAVAILABLE,
    Box,
    Line,
    Page,
    ReaderError,
    Transcript,
    reading_order,
)

API = "textract-detect-document-text"


class TextractRefused(Exception):
    """Textract refused the page (a 4xx: bad document, too large, access denied); never retried elsewhere."""


class TextractUnavailable(Exception):
    """Textract could not answer (a 5xx, throttling, the network); the fallback reader may be tried."""


class TextractReader:
    id = "textract"
    generative = False

    def __init__(self, detect: Callable[[bytes], dict]) -> None:
        self._detect = detect

    def read(self, page: Page) -> Transcript:
        image = page.image()  # the same bytes every reader refuses are refused before anything is sent
        png = io.BytesIO()
        image.save(png, format="PNG")
        try:
            answer = self._detect(png.getvalue())
        except TextractRefused:
            raise ReaderError(READER_REFUSED) from None
        except Exception:  # noqa: BLE001 — a provider's message can quote the page; the code says enough
            raise ReaderError(READER_UNAVAILABLE) from None
        lines = []
        for block in answer.get("Blocks", []):
            text = (block.get("Text") or "").strip()
            if block.get("BlockType") != "LINE" or not text:
                continue
            box = block["Geometry"]["BoundingBox"]
            left, top = float(box["Left"]), float(box["Top"])
            right, bottom = left + float(box["Width"]), top + float(box["Height"])
            confidence = min(1.0, max(0.0, float(block["Confidence"]) / 100))  # Textract says 0 to 100
            lines.append(Line(text, Box(max(0.0, left), max(0.0, top), min(1.0, right), min(1.0, bottom)), confidence))
        return Transcript(
            lines=reading_order(lines), version=f"{API} {answer.get('DetectDocumentTextModelVersion', 'unknown')}"
        )
