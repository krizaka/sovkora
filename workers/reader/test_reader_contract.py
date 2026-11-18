"""The ``DocumentReader`` contract, run against every adapter this repository holds (krizaka-internal#4198).

Every reader answers the same page with the same shape: the words of the page, a box inside the page and a confidence
in [0, 1] for every line, a name and a version, the same reading twice (a replay is free), and a coded refusal —
never a stack trace — for bytes that are not an image. PaddleOCR, docTR and Tesseract run for real here (CI installs
them); Textract answers from a recorded response until the account exists (no AWS call from CI).
"""

import json
import pathlib

import pytest

from conftest import PAGE_LINES
from document_reader.adapters.doctr_reader import DoctrReader
from document_reader.adapters.paddle import PaddleReader
from document_reader.adapters.tesseract import TesseractReader
from document_reader.adapters.textract import TextractReader
from document_reader.port import INPUT_INVALID, Page, PageKind, ReaderError

FIXTURE = pathlib.Path(__file__).parent / "fixtures" / "textract-detect-document-text.json"


def recorded_textract(_image: bytes) -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


READERS = {
    "tesseract": TesseractReader,
    "paddleocr": PaddleReader,
    "doctr": DoctrReader,
    "textract": lambda: TextractReader(detect=recorded_textract),
}


@pytest.fixture(scope="module", params=sorted(READERS))
def reader(request):
    return READERS[request.param]()


def _page(data: bytes) -> Page:
    return Page(data=data, kind=PageKind.SCREENSHOT)


def _words(text: str) -> set[str]:
    return set(text.upper().split())


def test_reads_every_word_of_the_page(reader, fixture_page_bytes):
    transcript = reader.read(_page(fixture_page_bytes))
    expected = {word for line in PAGE_LINES for word in line.split()}
    read = _words(" ".join(line.text for line in transcript.lines))
    assert expected <= read, f"{reader.id} missed {sorted(expected - read)}"


def test_every_line_has_text_a_box_inside_the_page_and_a_confidence(reader, fixture_page_bytes):
    transcript = reader.read(_page(fixture_page_bytes))
    assert transcript.lines
    for line in transcript.lines:
        assert line.text.strip()
        assert 0.0 <= line.box.left < line.box.right <= 1.0
        assert 0.0 <= line.box.top < line.box.bottom <= 1.0
        assert 0.0 <= line.confidence <= 1.0


def test_lines_come_in_reading_order(reader, fixture_page_bytes):
    tops = [line.box.top for line in reader.read(_page(fixture_page_bytes)).lines]
    assert tops == sorted(tops)


def test_says_who_read_and_which_version(reader, fixture_page_bytes):
    transcript = reader.read(_page(fixture_page_bytes))
    assert reader.id in READERS
    assert transcript.version.strip()
    assert reader.generative is False


def test_a_replay_gives_the_same_reading(reader, fixture_page_bytes):
    first = reader.read(_page(fixture_page_bytes))
    second = reader.read(_page(fixture_page_bytes))
    assert first == second


def test_bytes_that_are_no_image_are_refused_by_code(reader):
    with pytest.raises(ReaderError) as refused:
        reader.read(_page(b"%PDF-1.7 not an image"))
    assert refused.value.code == INPUT_INVALID
