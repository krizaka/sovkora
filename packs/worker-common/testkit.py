"""The tiny runner the pack workers' test files share: `python3 test_x.py` runs every `test_*` function of the file and exits 1 on a failure."""

import contextlib
import io
import json
import sys
from types import SimpleNamespace


def main(namespace: dict) -> None:
    failures = 0
    for name, fn in sorted(namespace.items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"  ok   {name}")
            except AssertionError as exc:
                failures += 1
                print(f"  FAIL {name}: {exc}")
    print(f"\n{'FAILED' if failures else 'all green'} — {failures} failure(s)")
    sys.exit(1 if failures else 0)


MARK = "MARK-9d2b-JOB-TEXT"
"""A text no failure answer may carry: a test puts it in the job or in an exception's message, then looks for it."""


class FakeChannel:
    """A channel that records what a worker publishes and acknowledges; no broker."""

    def __init__(self):
        self.published, self.acked, self.nacked = [], [], []

    def basic_publish(self, exchange, routing_key, body, properties):
        self.published.append((routing_key, json.loads(body)))

    def basic_ack(self, delivery_tag):
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag, requeue):
        self.nacked.append((delivery_tag, requeue))


def deliver(on_message, routing_key: str, job: dict) -> tuple:
    """Hand one job to a worker's consumer callback: ``(channel, stdout)`` — what it published and what it printed."""
    channel, out = FakeChannel(), io.StringIO()
    method = SimpleNamespace(delivery_tag=1, routing_key=routing_key)
    properties = SimpleNamespace(message_id=job.get("jobId"))
    with contextlib.redirect_stdout(out):
        on_message(channel, method, properties, json.dumps(job).encode())
    return channel, out.getvalue()


def assert_failure_leaks_nothing(channel, out: str, error: str, cause: str, job_id: str) -> None:
    """The job's one error event is ``error`` / ``cause``, and the marked text is in neither the event nor the log."""
    expected = [(f"job.{job_id}.error", {"jobId": job_id, "error": error, "cause": cause})]
    assert channel.published == expected, channel.published
    assert MARK not in json.dumps(channel.published), "the marked text reached the event"
    assert MARK not in out, "the marked text reached the log"
    assert job_id in out, out
    assert error in out, out


@contextlib.contextmanager
def handler_raising(handlers: dict, routing_key: str):
    """Swap the handler of ``routing_key`` for one that raises an exception whose message holds :data:`MARK`."""
    original = handlers[routing_key]

    def explode(*_args, **_kwargs):
        raise RuntimeError(f"cannot read: {MARK}")

    handlers[routing_key] = explode
    try:
        yield
    finally:
        handlers[routing_key] = original


def assert_free_text_reason_refused(refusal_class) -> None:
    """A reason outside the closed set is refused when the exception is built, and the refusal does not repeat it."""
    try:
        refusal_class(f"free text {MARK}")
    except ValueError as refused:
        message = str(refused)
    else:
        raise AssertionError("a free-text reason must be refused at construction")
    assert MARK not in message


def check_failure_answers(worker, fault: tuple, refusals: list) -> None:
    """The whole failure contract of a worker, in one call.

    ``worker`` is the worker's module (its ``_on_message``, its ``HANDLERS``, its ``InvalidJobPayload``). ``fault`` is
    ``(routing_key, payload)``: that handler raises an exception whose message holds :data:`MARK`, and the job
    answers with the exception's type name. Each of ``refusals`` is ``(routing_key, payload, reason)``: a job the worker
    refuses answers with ``reason``, never the value it was refused for. A reason outside the closed set is refused.
    """
    on_message, routing_key, payload = worker._on_message, *fault
    with handler_raising(worker.HANDLERS, routing_key):
        channel, out = deliver(on_message, routing_key, {"jobId": "fault-1", "payload": payload})
    assert_failure_leaks_nothing(channel, out, "RuntimeError", "EXECUTOR_FAULT", "fault-1")
    for number, (key, refused_payload, reason) in enumerate(refusals):
        job_id = f"refused-{number}"
        channel, out = deliver(on_message, key, {"jobId": job_id, "payload": refused_payload})
        assert_failure_leaks_nothing(channel, out, reason, "INPUT_INVALID", job_id)
    assert_free_text_reason_refused(worker.InvalidJobPayload)
