"""The document readers' one endpoint (krizaka-internal#4198): ``POST /v1/document-reads``, pages in, readings out.

The request carries the pages (``{"pages": [{"image": "<base64>", "kind": "photo"}]}``) and, from the authenticated door
of sovkora-conversation-service, the routing settings it read from its database for this call (``"routing": {...}``);
without them the seed (``routing.yaml``) applies. The reader is the routing's choice, never the product's: a product
reaches this worker only through the door, which never takes a routing from its caller.

The answer is one reading per page, in order. A failure
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
import threading
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Iterator, Mapping

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
from document_reader.routing import PAGE_BUDGET, ReaderRouting, RoutingError
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
#: The routing the door sent is out of its bounds: an operator's setting, named by its key, never a product's fault.
ROUTING_INVALID = "ROUTING_INVALID"
NOT_FOUND = "NOT_FOUND"

log = logging.getLogger("reader")


@dataclass(frozen=True)
class Answer:
    status: int
    body: dict


def _refused(code: str, status: int | None = None) -> Answer:
    return Answer(status or STATUS[code], {"code": code})


@dataclass(frozen=True)
class ReadRequest:
    pages: list[Page]
    #: The settings the door read for this call, or ``None`` for the seed.
    routing: Mapping[str, str] | None


def parse_request(raw: bytes) -> ReadRequest:
    """The pages and the routing of a request body (pure), or ``INPUT_INVALID``: a closed shape, decoded strictly."""
    try:
        body = json.loads(raw)
        entries = body["pages"]
        if not isinstance(entries, list) or not entries:
            raise ReaderError(INPUT_INVALID)
        pages = [
            Page(data=base64.b64decode(entry["image"], validate=True), kind=PageKind(entry["kind"]))
            for entry in entries
        ]
        routing = body.get("routing")
    except (ValueError, KeyError, TypeError, AttributeError, binascii.Error):
        raise ReaderError(INPUT_INVALID) from None
    if routing is not None and not (
        isinstance(routing, dict) and all(isinstance(value, str) for value in routing.values())
    ):
        raise ReaderError(INPUT_INVALID)
    return ReadRequest(pages=pages, routing=routing)


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
        request = parse_request(body)
        routing = None
        if request.routing is not None:
            routing = ReaderRouting.from_settings(request.routing, known=KNOWN, generative=GENERATIVE)
        pages = request.pages
        readings = service.read_pages(pages, routing)
    except RoutingError as wrong:
        return Answer(500, {"code": ROUTING_INVALID, "key": wrong.key})
    except ReaderError as failed:
        return _refused(failed.code)
    for page, reading in zip(pages, readings, strict=True):
        record(measurement(reading, page.kind))
    return Answer(200, {"readings": [reading_json(reading) for reading in readings]})


def load_routing(path: str) -> ReaderRouting:
    with open(path, encoding="utf-8") as handle:
        settings = {str(key): str(value) for key, value in (yaml.safe_load(handle) or {}).items()}
    return ReaderRouting.from_settings(settings, known=KNOWN, generative=GENERATIVE)


class Readers(Mapping[str, object]):
    """The readers of this host, each loaded the first time a routing names it, and once.

    The routing is a database row the door can change at any call, so a reader is not chosen when the server starts:
    switching the default to a reader this host has never loaded loads it then. One that cannot be loaded here is
    absent (read as unavailable, so the fallback answers) and is not tried again until the server restarts.
    """

    def __init__(self, factories: Mapping[str, Callable[[], object]]) -> None:
        self._factories = dict(factories)
        self._loaded: dict[str, object] = {}
        self._broken: set[str] = set()
        self._lock = threading.Lock()

    def __getitem__(self, name: str) -> object:
        with self._lock:
            if name not in self._loaded:
                if name not in self._factories or name in self._broken:
                    raise KeyError(name)
                try:
                    self._loaded[name] = self._factories[name]()
                except ReaderError:
                    self._broken.add(name)
                    log.warning("reader %s cannot be loaded on this host", name)
                    raise KeyError(name) from None
            return self._loaded[name]

    def __iter__(self) -> Iterator[str]:
        return iter(sorted(self._loaded))

    def __len__(self) -> int:
        return len(self._loaded)


def load_readers(routing: ReaderRouting) -> Readers:
    """This host's readers, the ones the seed names loaded now so the first call does not wait for a model.

    Textract is loaded only with a transport, which needs the AWS account: until then it is never routed to.
    """
    from document_reader.adapters.doctr_reader import DoctrReader  # noqa: PLC0415 — each engine loads only if named
    from document_reader.adapters.paddle import PaddleReader  # noqa: PLC0415
    from document_reader.adapters.tesseract import TesseractReader  # noqa: PLC0415

    readers = Readers({"paddleocr": PaddleReader, "doctr": DoctrReader, "tesseract": TesseractReader})
    for name in sorted({*routing.defaults.values(), *([routing.fallback] if routing.fallback else [])}):
        readers.get(name)
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
    port = int(os.environ.get("AI_READER_PORT", "8098"))
    listen = os.environ.get("AI_READER_LISTEN_IP", "127.0.0.1")
    log.info(json.dumps({"event": "reader.started", "port": port, "readers": sorted(readers)}))
    # Plain HTTP on purpose: an internal server bound to 127.0.0.1 by default, behind a service token, reached only
    # through the authenticated door of sovkora-conversation-service (krizaka-internal#4198), as workers/media does.
    ThreadingHTTPServer((listen, port), handler).serve_forever()  # NOSONAR python:S5332 - loopback, see above
    return 0


if __name__ == "__main__":
    sys.exit(main())
