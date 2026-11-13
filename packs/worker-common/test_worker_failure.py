"""The one safe failure answer (worker_failure.py): a coded reason or a type name, never text from a job.
Run: python3 -m pytest test_worker_failure.py -q. A fake channel, no broker."""

import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import testkit  # noqa: E402
import worker_failure as failure  # noqa: E402
import worker_loop as loop  # noqa: E402

MARK = testkit.MARK


class Refusal(failure.InvalidJobPayload):
    REASONS = frozenset({"DOCUMENT_MISSING", "FIELD_NOT_JSON"})


def _deliver(handle, job_id):
    on_message = loop.make_on_message("test", handle, set())
    return testkit.deliver(on_message, "job.test.do", {"jobId": job_id, "payload": {"text": MARK}})


def test_a_reason_is_one_code_of_the_workers_closed_set():
    assert Refusal("DOCUMENT_MISSING").reason == "DOCUMENT_MISSING"
    testkit.assert_free_text_reason_refused(Refusal)


def test_a_worker_that_declared_no_reasons_refuses_every_reason():
    with pytest.raises(ValueError, match="closed set"):
        failure.InvalidJobPayload("ANYTHING")


def test_a_refusal_answers_with_its_code_and_a_fault_with_its_type_only():
    refused = failure.failure_answer("j1", Refusal("FIELD_NOT_JSON"))
    assert refused == {"jobId": "j1", "error": "FIELD_NOT_JSON", "cause": "INPUT_INVALID"}
    crashed = failure.failure_answer("j2", RuntimeError(f"cannot read {MARK}"))
    assert crashed == {"jobId": "j2", "error": "RuntimeError", "cause": "EXECUTOR_FAULT"}


def test_the_log_line_names_the_routing_key_the_job_and_the_answer_only():
    line = failure.failure_log("test", "job.test.do", "j3", RuntimeError(f"cannot read {MARK}"))
    assert line == "[test] job.test.do -> job j3 failed: RuntimeError"


def test_the_loop_never_carries_an_exception_message_into_the_event_or_the_log():
    def crash(key, job):
        raise RuntimeError(f"cannot read {MARK}")

    channel, out = _deliver(crash, "j4")
    testkit.assert_failure_leaks_nothing(channel, out, "RuntimeError", "EXECUTOR_FAULT", "j4")
    assert "job.test.do" in out and channel.acked == [1]


def test_the_loop_answers_a_refusal_with_its_code_and_never_the_input():
    def refuse(key, job):
        raise Refusal("DOCUMENT_MISSING") from ValueError(MARK)

    channel, out = _deliver(refuse, "j5")
    testkit.assert_failure_leaks_nothing(channel, out, "DOCUMENT_MISSING", "INPUT_INVALID", "j5")
    assert json.dumps(channel.published).count("DOCUMENT_MISSING") == 1
