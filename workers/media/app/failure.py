"""The one safe answer to a failed job: a coded reason from a closed set, or the exception's type name (krizaka-internal#3376).

An exception's message can quote the prompt, the document or the file it was reading, the saga stores the error event and
the log outlives the job. So a failure leaves this worker as one of two things: the ``reason`` of a :class:`CodedReason`
(a code the worker wrote, from the closed set its class declares) or ``type(failure).__name__``. A free text is refused
when the exception is built, so a message taken from a job cannot reach the wire by construction.

This is the same rule as ``packs/worker-common/worker_failure.py``, carried here because the media image ships ``app/``
alone (``COPY app ./app``): it cannot import a sibling folder of the repository.
"""

from __future__ import annotations

from typing import ClassVar


class CodedReason(ValueError):
    """A refusal whose only content is a code from the closed set ``REASONS`` its subclass declares."""

    REASONS: ClassVar[frozenset[str]] = frozenset()

    def __init__(self, reason: str) -> None:
        if reason not in self.REASONS:
            raise ValueError("a reason is one code of the closed set of its class (REASONS)")
        super().__init__(reason)
        self.reason = reason
