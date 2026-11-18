"""Tesseract (Apache-2.0), the reference reader: the `tesseract` command, its TSV output grouped by line."""

from __future__ import annotations

import csv
import io
import shutil
import subprocess
from collections import defaultdict

from document_reader.port import (
    READER_TIMEOUT,
    READER_UNAVAILABLE,
    Box,
    Line,
    Page,
    ReaderError,
    Transcript,
    reading_order,
)

#: Tesseract's own languages for the pages Sovkora reads (French first, then English), when the host has them.
LANGUAGES = ("fra", "eng")
#: The page is one column of text lines, the layout of a scanned or photographed document.
PAGE_SEGMENTATION = "3"


class TesseractReader:
    id = "tesseract"
    generative = False

    def __init__(self, binary: str = "tesseract", timeout_s: float = 120) -> None:
        path = shutil.which(binary)
        if path is None:
            raise ReaderError(READER_UNAVAILABLE)
        self._binary = path
        self._timeout_s = timeout_s
        self._version = self._run("--version").splitlines()[0].strip()
        installed = set(self._run("--list-langs").split()[1:])
        self._languages = "+".join(lang for lang in LANGUAGES if lang in installed) or "eng"

    def _run(self, *args: str, data: bytes | None = None) -> str:
        try:
            done = subprocess.run(
                [self._binary, *args], input=data, capture_output=True, timeout=self._timeout_s, check=True
            )
        except subprocess.TimeoutExpired:
            raise ReaderError(READER_TIMEOUT) from None
        except (subprocess.CalledProcessError, OSError):
            raise ReaderError(READER_UNAVAILABLE) from None
        return done.stdout.decode("utf-8", "replace")

    def read(self, page: Page) -> Transcript:
        image = page.image()
        png = io.BytesIO()
        image.save(png, format="PNG")
        tsv = self._run(
            "stdin", "stdout", "-l", self._languages, "--psm", PAGE_SEGMENTATION, "tsv", data=png.getvalue()
        )
        return Transcript(lines=_lines(tsv, image.width, image.height), version=f"{self._version} {self._languages}")


def _lines(tsv: str, width: int, height: int) -> tuple[Line, ...]:
    """Tesseract's words (level 5) grouped by block, paragraph and line; a line's confidence is its words' mean."""
    words = defaultdict(list)
    for row in csv.DictReader(io.StringIO(tsv), delimiter="\t", quoting=csv.QUOTE_NONE):
        text = (row.get("text") or "").strip()
        if row.get("level") != "5" or not text or float(row["conf"]) < 0:
            continue
        words[(int(row["block_num"]), int(row["par_num"]), int(row["line_num"]))].append(row)
    lines = []
    for group in words.values():
        left = min(int(w["left"]) for w in group)
        top = min(int(w["top"]) for w in group)
        right = max(int(w["left"]) + int(w["width"]) for w in group)
        bottom = max(int(w["top"]) + int(w["height"]) for w in group)
        confidence = sum(float(w["conf"]) for w in group) / len(group) / 100
        text = " ".join(w["text"].strip() for w in group)
        lines.append(Line(text, Box.of_pixels(left, top, right, bottom, width, height), min(1.0, confidence)))
    return reading_order(lines)
