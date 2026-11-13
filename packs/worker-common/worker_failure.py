"""The one safe answer to a failed job: what a pack worker may put in an event or a log line (krizaka-internal#3376).

An exception's message can quote the document it was reading, and a saga stores the event while a log outlives the job.
So a failure leaves a worker as one of two things and nothing else:

* a **coded reason** from a closed set the worker declares (``InvalidJobPayload`` — the one cause that bills), or
* the exception's **type name** (any other failure, cause ``EXECUTOR_FAULT``).

A free-text reason is refused when the exception is built, so a message taken from the job cannot reach the wire by
construction. This module imports no broker library: every worker, with its own loop or on ``worker_loop.run``, uses it.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import ClassVar

INPUT_INVALID = "INPUT_INVALID"
EXECUTOR_FAULT = "EXECUTOR_FAULT"


class InvalidJobPayload(ValueError):
    """The worker checked the payload and found it unusable — the one cause that bills.

    A worker subclasses it and declares ``REASONS``, its closed set of codes; ``reason`` must be one of them.
    """

    REASONS: ClassVar[frozenset[str]] = frozenset()

    def __init__(self, reason: str) -> None:
        if reason not in self.REASONS:
            raise ValueError("an invalid-payload reason is one code of the worker's closed set (REASONS)")
        super().__init__(reason)
        self.reason = reason


def failure_answer(job_id: str | None, failure: Exception) -> dict:
    """The body of ``job.{id}.error``: a coded reason (``INPUT_INVALID``) or the type name (``EXECUTOR_FAULT``)."""
    if isinstance(failure, InvalidJobPayload):
        return {"jobId": job_id, "error": failure.reason, "cause": INPUT_INVALID}
    return {"jobId": job_id, "error": type(failure).__name__, "cause": EXECUTOR_FAULT}


def failure_log(label: str, routing_key: str, job_id: str | None, failure: Exception) -> str:
    """The log line of a failed job: routing key, job id and what the event says — never the exception's message."""
    answer = failure_answer(job_id, failure)
    return f"[{label}] {routing_key} -> job {job_id} failed: {answer['error']}"


def report_failure(
    emit: Callable, channel, label: str, routing_key: str, job_id: str | None, failure: Exception
) -> None:
    """Log a failed job and publish its error event through the worker's own ``emit(channel, job_id, suffix, body)``."""
    print(failure_log(label, routing_key, job_id, failure), flush=True)
    emit(channel, job_id, "error", failure_answer(job_id, failure))
