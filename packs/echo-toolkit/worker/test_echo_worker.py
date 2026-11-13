"""The reference worker's failure answer: a coded reason or a type name, never text from the job.
Run: python3 -m pytest test_echo_worker.py -q. A fake channel, no broker."""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "worker-common"))
import echo_worker as worker  # noqa: E402
import testkit  # noqa: E402


def _deliver(job_id):
    return testkit.deliver(worker._on_message, "job.echo.reverse", {"jobId": job_id, "payload": {"text": testkit.MARK}})


def test_a_failure_carries_the_type_name_only_never_the_text_of_the_exception(monkeypatch):
    def explode(job):
        raise RuntimeError(f"cannot read: {testkit.MARK}")

    monkeypatch.setattr(worker, "_handle", explode)
    channel, out = _deliver("echo-fault")
    testkit.assert_failure_leaks_nothing(channel, out, "RuntimeError", "EXECUTOR_FAULT", "echo-fault")
    assert "job.echo.reverse" in out and channel.acked == [1]


def test_a_refused_payload_answers_with_its_code_and_never_the_input(monkeypatch):
    def refuse(job):
        raise worker.InvalidJobPayload("TEXT_MISSING") from ValueError(testkit.MARK)

    monkeypatch.setattr(worker, "_handle", refuse)
    channel, out = _deliver("echo-bad")
    testkit.assert_failure_leaks_nothing(channel, out, "TEXT_MISSING", "INPUT_INVALID", "echo-bad")


def test_a_reason_outside_the_closed_set_is_refused():
    testkit.assert_free_text_reason_refused(worker.InvalidJobPayload)
