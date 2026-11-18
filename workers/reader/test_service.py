"""The read service: the route chosen by the settings, the fallback, the page budget, and what each call records."""

import hashlib
import threading

import pytest

from document_reader.port import (
    BUDGET_EXHAUSTED,
    INPUT_INVALID,
    READER_REFUSED,
    READER_TIMEOUT,
    READER_UNAVAILABLE,
    Box,
    Line,
    Page,
    PageKind,
    ReaderError,
    Transcript,
)
from document_reader.routing import ReaderRouting
from document_reader.service import ReadService

PAGE = Page(data=b"page-bytes", kind=PageKind.PHOTO)


class FakeReader:
    """A reader that answers what it is told, or fails with a code; counts its calls."""

    generative = False

    def __init__(self, reader_id, *, fails=None, text="hello", block=None):
        self.id = reader_id
        self.fails = fails
        self.text = text
        self.block = block
        self.calls = 0

    def read(self, page):
        self.calls += 1
        if self.block is not None:
            self.block.wait(5)
        if self.fails:
            raise ReaderError(self.fails)
        return Transcript(lines=(Line(self.text, Box(0.1, 0.1, 0.9, 0.2), 0.9),), version=f"{self.id}-1.0")


class Clock:
    """A monotonic clock in seconds that moves 0.25 s each time it is read."""

    def __init__(self):
        self.now = 100.0

    def __call__(self):
        self.now += 0.25
        return self.now


def routing(**changes):
    settings = {
        "reader.default.pdf-scan": "paddleocr",
        "reader.default.screenshot": "paddleocr",
        "reader.default.photo": "doctr",
        "reader.fallback": "tesseract",
        "reader.vlm.enabled": "false",
        "reader.page-budget": "2",
        "reader.timeout-ms": "1000",
        "reader.cost-micros.doctr": "40",
        "reader.cost-micros.tesseract": "7",
        **changes,
    }
    return ReaderRouting.from_settings(settings, known={"paddleocr", "doctr", "tesseract"}, generative=set())


def service(readers, **changes):
    return ReadService(readers={r.id: r for r in readers}, routing=routing(**changes), clock=Clock())


def test_the_kind_picks_the_default_reader_and_the_call_records_cost_and_latency():
    doctr, paddle = FakeReader("doctr"), FakeReader("paddleocr")
    reading = service([doctr, paddle, FakeReader("tesseract")]).read(PAGE)
    assert (doctr.calls, paddle.calls) == (1, 0)
    assert reading.reader == "doctr"
    assert reading.version == "doctr-1.0"
    assert reading.text == "hello"
    assert reading.cost_micros == 40
    assert reading.latency_ms == 250
    assert reading.fallback_from is None


def test_switching_the_setting_switches_the_reader_without_touching_the_caller():
    paddle = FakeReader("paddleocr")
    reading = service(
        [FakeReader("doctr"), paddle, FakeReader("tesseract")], **{"reader.default.photo": "paddleocr"}
    ).read(PAGE)
    assert reading.reader == "paddleocr"
    assert paddle.calls == 1


def test_the_key_is_the_page_hash_the_reader_and_its_version():
    reading = service([FakeReader("doctr"), FakeReader("paddleocr"), FakeReader("tesseract")]).read(PAGE)
    assert reading.page_hash == hashlib.sha256(b"page-bytes").hexdigest()
    assert reading.key == f"{reading.page_hash}:doctr:doctr-1.0"


@pytest.mark.parametrize("code", [READER_UNAVAILABLE, READER_TIMEOUT])
def test_an_unavailable_reader_falls_back_and_says_so(code):
    fallback = FakeReader("tesseract", text="from the fallback")
    reading = service([FakeReader("doctr", fails=code), FakeReader("paddleocr"), fallback]).read(PAGE)
    assert reading.reader == "tesseract"
    assert reading.fallback_from == "doctr"
    assert reading.text == "from the fallback"
    assert reading.cost_micros == 7


@pytest.mark.parametrize("code", [READER_REFUSED, INPUT_INVALID])
def test_a_refusal_is_never_retried_on_another_reader(code):
    fallback = FakeReader("tesseract")
    with pytest.raises(ReaderError) as refused:
        service([FakeReader("doctr", fails=code), FakeReader("paddleocr"), fallback]).read(PAGE)
    assert refused.value.code == code
    assert fallback.calls == 0


def test_without_a_fallback_the_failure_is_the_answer():
    with pytest.raises(ReaderError) as refused:
        service(
            [FakeReader("doctr", fails=READER_UNAVAILABLE), FakeReader("paddleocr"), FakeReader("tesseract")],
            **{"reader.fallback": ""},
        ).read(PAGE)
    assert refused.value.code == READER_UNAVAILABLE


def test_a_reader_slower_than_the_timeout_is_a_timeout():
    gate = threading.Event()
    slow = FakeReader("doctr", block=gate)
    read = service(
        [slow, FakeReader("paddleocr"), FakeReader("tesseract", fails=READER_UNAVAILABLE)],
        **{"reader.timeout-ms": "100"},
    )
    try:
        with pytest.raises(ReaderError) as refused:
            read.read(PAGE)
    finally:
        gate.set()
    assert refused.value.code == READER_UNAVAILABLE  # the fallback was tried after the timeout, and it was down too
    assert slow.calls == 1


def test_a_route_to_a_reader_that_is_not_installed_is_unavailable():
    with pytest.raises(ReaderError) as refused:
        service([FakeReader("paddleocr")], **{"reader.fallback": ""}).read(PAGE)
    assert refused.value.code == READER_UNAVAILABLE


def test_more_pages_than_the_budget_are_refused_before_any_is_read():
    doctr = FakeReader("doctr")
    with pytest.raises(ReaderError) as refused:
        service([doctr, FakeReader("paddleocr"), FakeReader("tesseract")]).read_pages([PAGE, PAGE, PAGE])
    assert refused.value.code == BUDGET_EXHAUSTED
    assert doctr.calls == 0


def test_pages_within_the_budget_are_each_read_and_measured():
    readings = service([FakeReader("doctr"), FakeReader("paddleocr"), FakeReader("tesseract")]).read_pages([PAGE, PAGE])
    assert [r.latency_ms for r in readings] == [250, 250]
    assert [r.cost_micros for r in readings] == [40, 40]


def test_an_empty_page_is_refused_when_built():
    with pytest.raises(ReaderError) as refused:
        Page(data=b"", kind=PageKind.PHOTO)
    assert refused.value.code == INPUT_INVALID


def test_a_page_over_the_size_bound_is_refused_when_built():
    with pytest.raises(ReaderError) as refused:
        Page(data=b"x" * (2 * 1024 * 1024 + 1), kind=PageKind.PHOTO)
    assert refused.value.code == INPUT_INVALID


def test_an_error_carries_a_code_never_free_text():
    with pytest.raises(ValueError):
        ReaderError("the page said: TOTAL 1234.56")
