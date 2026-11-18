"""The read service: routes a page to its reader, falls back once, and measures every call (krizaka-internal#4198).

Each reading carries what it cost (the routing's price of the reader that answered) and how long it took (the wall time
of the whole call, fallback included, on the injected monotonic clock). Nothing is kept once the reading is returned.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeout
from typing import Callable, Mapping, Sequence

from document_reader.port import (
    BUDGET_EXHAUSTED,
    READER_TIMEOUT,
    READER_UNAVAILABLE,
    RETRYABLE,
    DocumentReader,
    Page,
    ReaderError,
    Reading,
    Transcript,
)
from document_reader.routing import ReaderRouting


class ReadService:
    def __init__(
        self,
        *,
        readers: Mapping[str, DocumentReader],
        routing: ReaderRouting,
        clock: Callable[[], float],
        workers: int = 2,
    ) -> None:
        self._readers = dict(readers)
        self._routing = routing
        self._clock = clock
        # A reader past its timeout keeps its thread until it returns: the pool bounds how many can pile up.
        self._pool = ThreadPoolExecutor(max_workers=workers, thread_name_prefix="reader")

    def read_pages(self, pages: Sequence[Page]) -> list[Reading]:
        """Every page read, or ``BUDGET_EXHAUSTED`` before any is when there are more than the budget allows."""
        if len(pages) > self._routing.page_budget:
            raise ReaderError(BUDGET_EXHAUSTED)
        return [self.read(page) for page in pages]

    def read(self, page: Page) -> Reading:
        started = self._clock()
        chosen = self._routing.default_for(page.kind)
        try:
            transcript, reader = self._attempt(chosen, page), chosen
            fallback_from = None
        except ReaderError as failed:
            fallback = self._routing.fallback
            if failed.code not in RETRYABLE or fallback is None or fallback == chosen:
                raise
            transcript, reader = self._attempt(fallback, page), fallback
            fallback_from = chosen
        latency_ms = round((self._clock() - started) * 1000)
        return Reading(
            text="\n".join(line.text for line in transcript.lines),
            lines=transcript.lines,
            reader=reader,
            version=transcript.version,
            generative=self._readers[reader].generative,
            page_hash=page.sha256,
            cost_micros=self._routing.cost_micros(reader),
            latency_ms=latency_ms,
            fallback_from=fallback_from,
        )

    def _attempt(self, reader_id: str, page: Page) -> Transcript:
        reader = self._readers.get(reader_id)
        if reader is None:
            raise ReaderError(READER_UNAVAILABLE)
        future = self._pool.submit(reader.read, page)
        try:
            return future.result(timeout=self._routing.timeout_ms / 1000)
        except FutureTimeout:
            raise ReaderError(READER_TIMEOUT) from None
