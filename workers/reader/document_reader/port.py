"""The ``DocumentReader`` port (krizaka-internal#4198): one page in, its lines out, whatever model reads it.

A product never knows which reader answered until it reads ``Reading.reader``: the reader is chosen by the routing
settings (``routing.py``), never by the caller. Nothing here imports a vendor SDK; an adapter that needs one keeps it
inside its own file, and no type of it crosses this module.

The page comes in the call and nothing keeps it: a reader reads and returns, the service measures and returns.
"""

from __future__ import annotations

import enum
import hashlib
import io
from dataclasses import dataclass
from typing import Protocol

from PIL import Image, UnidentifiedImageError

#: The reader is down, not installed, or its provider answered 5xx: the fallback may be tried.
READER_UNAVAILABLE = "READER_UNAVAILABLE"
#: The reader did not answer within the routing's timeout: the fallback may be tried.
READER_TIMEOUT = "READER_TIMEOUT"
#: The reader positively refused the page (its provider said 4xx): never retried on another reader.
READER_REFUSED = "READER_REFUSED"
#: More pages than the routing's page budget: refused before any is read.
BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED"
#: The bytes are not an image a reader can read, or break the size bound.
INPUT_INVALID = "INPUT_INVALID"

CODES = frozenset({READER_UNAVAILABLE, READER_TIMEOUT, READER_REFUSED, BUDGET_EXHAUSTED, INPUT_INVALID})
#: The codes after which the fallback reader is tried; a refusal or a bad input is the answer.
RETRYABLE = frozenset({READER_UNAVAILABLE, READER_TIMEOUT})

#: One page at most this large (the bound of the capture pipeline: 12 pages of 2 MB).
MAX_PAGE_BYTES = 2 * 1024 * 1024


class ReaderError(Exception):
    """A failure by code. Its message is the code: a reader's own message can quote the page it was reading."""

    def __init__(self, code: str) -> None:
        if code not in CODES:
            raise ValueError("a reader error is one of the codes, never free text")
        super().__init__(code)
        self.code = code


class PageKind(enum.Enum):
    """What the page is, declared by the product: the routing picks a reader per kind."""

    PDF_SCAN = "pdf-scan"
    SCREENSHOT = "screenshot"
    PHOTO = "photo"


@dataclass(frozen=True)
class Page:
    """One prepared page: an image (PNG, JPEG, WebP or TIFF; a PDF page is rasterised by the product) and its kind."""

    data: bytes
    kind: PageKind

    def __post_init__(self) -> None:
        if not self.data or len(self.data) > MAX_PAGE_BYTES:
            raise ReaderError(INPUT_INVALID)

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.data).hexdigest()

    def image(self) -> Image.Image:
        """The page decoded as RGB, or ``INPUT_INVALID``: every adapter refuses the same bytes the same way."""
        try:
            with Image.open(io.BytesIO(self.data)) as opened:
                return opened.convert("RGB")
        except (UnidentifiedImageError, OSError, ValueError):
            raise ReaderError(INPUT_INVALID) from None


@dataclass(frozen=True)
class Box:
    """A line's box as fractions of the page (0 is the left or top edge, 1 the right or bottom one)."""

    left: float
    top: float
    right: float
    bottom: float

    @classmethod
    def of_pixels(cls, left: float, top: float, right: float, bottom: float, width: int, height: int) -> Box:
        """A box in pixels of a ``width`` x ``height`` page, as fractions clamped to the page."""

        def clamp(value: float) -> float:
            return min(1.0, max(0.0, value))

        return cls(clamp(left / width), clamp(top / height), clamp(right / width), clamp(bottom / height))


@dataclass(frozen=True)
class Line:
    """One line of text, its box and the reader's confidence in it, in [0, 1]."""

    text: str
    box: Box
    confidence: float


@dataclass(frozen=True)
class Transcript:
    """What an adapter answers: the page's lines in reading order and the exact version that read them."""

    lines: tuple[Line, ...]
    version: str


def reading_order(lines: list[Line]) -> tuple[Line, ...]:
    """Lines top to bottom, then left to right; the same order whichever engine found them."""
    return tuple(sorted(lines, key=lambda line: (round(line.box.top, 2), line.box.left)))


class DocumentReader(Protocol):
    """A model that reads a page. ``generative`` readers (a vision-language model) are routed only behind the switch."""

    id: str
    generative: bool

    def read(self, page: Page) -> Transcript: ...


@dataclass(frozen=True)
class Reading:
    """The answer to one page: what was read, by whom, and what the call cost and took.

    ``key`` is the page's hash, the reader and its version: the same page read again by the same reader version is the
    same reading, which is what lets a replay be answered without being billed twice.
    """

    text: str
    lines: tuple[Line, ...]
    reader: str
    version: str
    generative: bool
    page_hash: str
    cost_micros: int
    latency_ms: int
    fallback_from: str | None

    @property
    def key(self) -> str:
        return f"{self.page_hash}:{self.reader}:{self.version}"
