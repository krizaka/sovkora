"""The one endpoint: a product sends pages and gets readings; the reader is the routing's, the call is measured."""

import base64
import json
import pathlib

import pytest

import reader_server
from document_reader.port import READER_REFUSED, READER_UNAVAILABLE, Box, Line, PageKind, ReaderError, Transcript
from document_reader.routing import ReaderRouting
from document_reader.service import ReadService

HERE = pathlib.Path(__file__).parent


class FakeReader:
    generative = False

    def __init__(self, reader_id, fails=None):
        self.id = reader_id
        self.fails = fails

    def read(self, page):
        if self.fails:
            raise ReaderError(self.fails)
        return Transcript(lines=(Line("TOTAL 1234.56", Box(0.1, 0.4, 0.5, 0.5), 0.97),), version=f"{self.id}-1")


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        self.now += 0.1
        return self.now


def routing(**changes):
    seed = reader_server.load_routing(str(HERE / "routing.yaml"))
    settings = {
        **{f"reader.default.{kind.value}": seed.default_for(kind) for kind in PageKind},
        "reader.fallback": seed.fallback or "",
        "reader.page-budget": "2",
        "reader.timeout-ms": "1000",
        "reader.cost-micros.paddleocr": "12",
        **changes,
    }
    return ReaderRouting.from_settings(settings, known=reader_server.KNOWN, generative=set())


def call(readers, body, *, token=None, auth=None, method="POST", path=reader_server.PATH, **changes):
    recorded = []
    service = ReadService(readers=readers, routing=routing(**changes), clock=Clock())
    done = reader_server.answer(
        service,
        recorded.append,
        method=method,
        path=path,
        headers={"Authorization": auth} if auth else {},
        body=body,
        token=token,
        readers=readers,
    )
    return done, recorded


def pages(*kinds, image=b"page"):
    return json.dumps({"pages": [{"image": base64.b64encode(image).decode(), "kind": k} for k in kinds]}).encode()


READERS = {"paddleocr": FakeReader("paddleocr"), "tesseract": FakeReader("tesseract")}


def test_the_seed_routes_every_kind_to_a_reader_this_worker_knows():
    seed = reader_server.load_routing(str(HERE / "routing.yaml"))
    assert {seed.default_for(kind) for kind in PageKind} <= reader_server.KNOWN
    assert seed.vlm_enabled is False


def test_a_product_sends_pages_and_gets_one_reading_each_in_order():
    done, _ = call(READERS, pages("photo", "pdf-scan"))
    assert done.status == 200
    first = done.body["readings"][0]
    assert first["reader"] == "paddleocr"
    assert first["text"] == "TOTAL 1234.56"
    assert first["lines"][0] == {
        "text": "TOTAL 1234.56",
        "box": {"left": 0.1, "top": 0.4, "right": 0.5, "bottom": 0.5},
        "confidence": 0.97,
    }
    assert first["key"] == f"{first['pageHash']}:paddleocr:paddleocr-1"
    assert len(done.body["readings"]) == 2


def test_switching_the_default_is_the_routing_the_request_is_unchanged():
    done, _ = call(READERS, pages("photo"), **{"reader.default.photo": "tesseract"})
    assert done.body["readings"][0]["reader"] == "tesseract"


def test_every_call_records_its_cost_and_latency_and_no_word_of_the_page():
    done, recorded = call(READERS, pages("screenshot"))
    assert done.body["readings"][0]["costMicros"] == 12
    assert done.body["readings"][0]["latencyMs"] == 100
    assert recorded == [
        {
            "event": "document.read",
            "kind": "screenshot",
            "reader": "paddleocr",
            "version": "paddleocr-1",
            "costMicros": 12,
            "latencyMs": 100,
            "fallbackFrom": None,
            "lines": 1,
        }
    ]
    assert "TOTAL" not in json.dumps(recorded)


@pytest.mark.parametrize(
    "body",
    [
        b"not json",
        b"{}",
        json.dumps({"pages": []}).encode(),
        json.dumps({"pages": [{"image": "not base64!", "kind": "photo"}]}).encode(),
        json.dumps({"pages": [{"image": "cGFnZQ==", "kind": "selfie"}]}).encode(),
        json.dumps({"pages": [{"kind": "photo"}]}).encode(),
        json.dumps({"pages": "cGFnZQ=="}).encode(),
    ],
)
def test_a_malformed_request_is_input_invalid(body):
    done, recorded = call(READERS, body)
    assert (done.status, done.body) == (400, {"code": "INPUT_INVALID"})
    assert recorded == []


def test_more_pages_than_the_budget_is_413_before_any_is_read():
    done, recorded = call(READERS, pages("photo", "photo", "photo"))
    assert (done.status, done.body) == (413, {"code": "BUDGET_EXHAUSTED"})
    assert recorded == []


def test_a_body_over_the_bound_is_refused_unread():
    done, _ = call(READERS, b"x" * (reader_server.MAX_BODY + 1))
    assert (done.status, done.body) == (413, {"code": "INPUT_INVALID"})


@pytest.mark.parametrize(("code", "status"), [(READER_UNAVAILABLE, 503), (READER_REFUSED, 422)])
def test_a_reader_failure_is_its_code_and_status(code, status):
    readers = {"paddleocr": FakeReader("paddleocr", fails=code), "tesseract": FakeReader("tesseract", fails=code)}
    done, _ = call(readers, pages("photo"))
    assert (done.status, done.body) == (status, {"code": code})


def test_the_fallback_answers_when_the_default_is_down_and_says_so():
    readers = {"paddleocr": FakeReader("paddleocr", fails=READER_UNAVAILABLE), "tesseract": FakeReader("tesseract")}
    done, recorded = call(readers, pages("photo"))
    assert done.body["readings"][0]["reader"] == "tesseract"
    assert recorded[0]["fallbackFrom"] == "paddleocr"


def test_with_a_token_a_call_without_it_is_refused():
    assert call(READERS, pages("photo"), token="s3cret")[0].status == 401
    assert call(READERS, pages("photo"), token="s3cret", auth="Bearer wrong")[0].status == 401
    assert call(READERS, pages("photo"), token="s3cret", auth="Bearer s3cret")[0].status == 200


def test_health_names_the_readers_loaded_and_other_paths_are_404():
    assert call(READERS, b"", method="GET", path=reader_server.HEALTH)[0].body == {
        "readers": ["paddleocr", "tesseract"]
    }
    assert call(READERS, b"", method="GET", path="/v1/other")[0].status == 404
    assert call(READERS, pages("photo"), method="GET")[0].status == 404


def test_the_readers_loaded_are_the_ones_the_routing_names():
    assert set(reader_server.load_readers(routing())) == {"paddleocr", "tesseract"}


def test_over_http_a_product_gets_its_readings_and_an_oversized_body_is_refused_unread():
    import threading
    import urllib.error
    import urllib.request
    from http.server import ThreadingHTTPServer

    service = ReadService(readers=READERS, routing=routing(), clock=Clock())
    handler = reader_server.make_handler(service, lambda _line: None, token=None, readers=READERS)
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_port}{reader_server.PATH}"
    try:
        request = urllib.request.Request(url, data=pages("photo"), method="POST")
        with urllib.request.urlopen(request, timeout=5) as response:
            assert response.status == 200
            assert json.loads(response.read())["readings"][0]["reader"] == "paddleocr"
        oversized = urllib.request.Request(url, data=b"{}", method="POST")
        oversized.add_header("Content-Length", str(reader_server.MAX_BODY + 1))
        with pytest.raises(urllib.error.HTTPError) as refused:
            urllib.request.urlopen(oversized, timeout=5)
        assert refused.value.code == 413
    finally:
        server.shutdown()
        server.server_close()
