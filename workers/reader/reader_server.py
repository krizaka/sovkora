"""The document readers' one endpoint (krizaka-internal#4198): ``POST /v1/document-reads``, pages in, readings out.

The request carries the pages (``{"pages": [{"image": "<base64>", "kind": "photo"}]}``) and nothing else: the reader is
the routing's choice (``routing.yaml``), never the caller's. The answer is one reading per page, in order. A failure
is a code and an HTTP status, never a message: an engine's message can quote the page.

Each call writes one measurement line (reader, version, kind, cost, latency, fallback) and no word of the page; the page
is never written anywhere. The server listens on the loopback by default; behind a network it demands the service token.
"""

from __future__ import annotations

import base64
import binascii
import hmac
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Mapping

import yaml

from document_reader.port import (
    BUDGET_EXHAUSTED,
    INPUT_INVALID,
    MAX_PAGE_BYTES,
    READER_REFUSED,
    READER_TIMEOUT,
    READER_UNAVAILABLE,
    Page,
    PageKind,
    ReaderError,
    Reading,
)
from document_reader.routing import PAGE_BUDGET, ReaderRouting
from document_reader.service import ReadService

PATH = "/v1/document-reads"
HEALTH = "/v1/health"
#: The readers this worker knows; which of them can be loaded on this host is said by the health answer.
KNOWN = {"paddleocr", "doctr", "tesseract", "textract"}
GENERATIVE: set[str] = set()
#: The HTTP status of each code: a refusal of the request (4xx) or a reader that cannot answer now (5xx).
STATUS = {
    INPUT_INVALID: 400,
    BUDGET_EXHAUSTED: 413,
    READER_REFUSED: 422,
    READER_UNAVAILABLE: 503,
    READER_TIMEOUT: 504,
}
#: A request body at most this large: every page of the budget at its size bound, in base64, with room for the JSON.
MAX_BODY = PAGE_BUDGET[1] * (MAX_PAGE_BYTES * 4 // 3 + 4096)
UNAUTHORISED = "UNAUTHORISED"
NOT_FOUND = "NOT_FOUND"

log = logging.getLogger("reader")


@dataclass(frozen=True)
class Answer:
    status: int
    body: dict


def _refused(code: str, status: int | None = None) -> Answer:
    return Answer(status or STATUS[code], {"code": code})


def parse_pages(raw: bytes) -> list[Page]:
    """The pages of a request body (pure), or ``INPUT_INVALID``: a closed shape, decoded strictly."""
    try:
        body = json.loads(raw)
        entries = body["pages"]
        if not isinstance(entries, list) or not entries:
            raise ReaderError(INPUT_INVALID)
        return [
            Page(data=base64.b64decode(entry["image"], validate=True), kind=PageKind(entry["kind"]))
            for entry in entries
        ]
    except (ValueError, KeyError, TypeError, binascii.Error):
        raise ReaderError(INPUT_INVALID) from None


def reading_json(reading: Reading) -> dict:
    """A reading on the wire: the names a product reads, the boxes as fractions of the page."""
    return {
        "text": reading.text,
        "lines": [
            {
                "text": line.text,
                "box": {"left": line.box.left, "top": line.box.top, "right": line.box.right, "bottom": line.box.bottom},
                "confidence": line.confidence,
            }
            for line in reading.lines
        ],
        "reader": reading.reader,
        "version": reading.version,
        "generative": reading.generative,
        "pageHash": reading.page_hash,
        "key": reading.key,
        "costMicros": reading.cost_micros,
        "latencyMs": reading.latency_ms,
        "fallbackFrom": reading.fallback_from,
    }


def measurement(reading: Reading, kind: PageKind) -> dict:
    """What one call records: who read, in which version, at what cost and speed. Never a word of the page."""
    return {
        "event": "document.read",
        "kind": kind.value,
        "reader": reading.reader,
        "version": reading.version,
        "costMicros": reading.cost_micros,
        "latencyMs": reading.latency_ms,
        "fallbackFrom": reading.fallback_from,
        "lines": len(reading.lines),
    }


def answer(
    service: ReadService,
    record: Callable[[dict], None],
    *,
    method: str,
    path: str,
    headers: Mapping[str, str],
    body: bytes,
    token: str | None,
    readers: Mapping[str, object],
) -> Answer:
    """The whole request, decided (pure but for the readers it calls): the route, the token, the pages, the readings."""
    route = path.split("?", 1)[0].rstrip("/")
    if token and not hmac.compare_digest(headers.get("Authorization", ""), f"Bearer {token}"):
        return _refused(UNAUTHORISED, 401)
    if method == "GET" and route == HEALTH:
        return Answer(200, {"readers": sorted(readers)})
    if method != "POST" or route != PATH:
        return _refused(NOT_FOUND, 404)
    if len(body) > MAX_BODY:
        return _refused(INPUT_INVALID, 413)
    try:
        pages = parse_pages(body)
        readings = service.read_pages(pages)
    except ReaderError as failed:
        return _refused(failed.code)
    for page, reading in zip(pages, readings, strict=True):
        record(measurement(reading, page.kind))
    return Answer(200, {"readings": [reading_json(reading) for reading in readings]})


def load_routing(path: str) -> ReaderRouting:
    with open(path, encoding="utf-8") as handle:
        settings = {str(key): str(value) for key, value in (yaml.safe_load(handle) or {}).items()}
    return ReaderRouting.from_settings(settings, known=KNOWN, generative=GENERATIVE)


def load_readers(routing: ReaderRouting) -> dict:
    """The readers the routing names that this host can load; one that cannot is left out and read as unavailable.

    Textract is loaded only with a transport, which needs the AWS account: until then it is never routed to.
    """
    from document_reader.adapters.doctr_reader import DoctrReader  # noqa: PLC0415 — each engine loads only if named
    from document_reader.adapters.paddle import PaddleReader  # noqa: PLC0415
    from document_reader.adapters.tesseract import TesseractReader  # noqa: PLC0415

    factories = {"paddleocr": PaddleReader, "doctr": DoctrReader, "tesseract": TesseractReader}
    named = {*routing.defaults.values(), *([routing.fallback] if routing.fallback else [])}
    readers = {}
    for name in sorted(named & factories.keys()):
        try:
            readers[name] = factories[name]()
        except ReaderError:
            log.warning("reader %s cannot be loaded on this host", name)
    return readers


def make_handler(
    service: ReadService, record: Callable[[dict], None], *, token: str | None, readers: Mapping[str, object]
) -> type[BaseHTTPRequestHandler]:
    """The HTTP side of :func:`answer`: a body over the bound is refused before a byte of it is read."""

    class Handler(BaseHTTPRequestHandler):
        def _serve(self) -> None:
            length = int(self.headers.get("Content-Length") or 0)
            if length > MAX_BODY:
                done = _refused(INPUT_INVALID, 413)
            else:
                done = answer(
                    service,
                    record,
                    method=self.command,
                    path=self.path,
                    headers=dict(self.headers.items()),
                    body=self.rfile.read(length) if length else b"",
                    token=token,
                    readers=readers,
                )
            payload = json.dumps(done.body).encode()
            self.send_response(done.status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        do_GET = _serve  # http.server's names
        do_POST = _serve

        def log_message(self, *_args) -> None:  # the default line carries the path and the client: nothing is needed
            return

    return Handler


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    here = os.path.dirname(os.path.abspath(__file__))
    routing = load_routing(os.environ.get("AI_READER_ROUTING_FILE", os.path.join(here, "routing.yaml")))
    readers = load_readers(routing)
    service = ReadService(readers=readers, routing=routing, clock=time.monotonic)

    def record(line: dict) -> None:
        log.info(json.dumps(line, sort_keys=True))

    handler = make_handler(service, record, token=os.environ.get("AI_READER_SERVICE_TOKEN") or None, readers=readers)
    port = int(os.environ.get("AI_READER_PORT", "8095"))
    listen = os.environ.get("AI_READER_LISTEN_IP", "127.0.0.1")
    log.info(json.dumps({"event": "reader.started", "port": port, "readers": sorted(readers)}))
    ThreadingHTTPServer((listen, port), handler).serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
