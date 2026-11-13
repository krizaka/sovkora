"""A failed job answers with a type name or a coded reason — never a message that can hold document text.
Run: python3 -m pytest test_failure_answer.py -q. A fake channel, no broker."""

import json
import os
import sys
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import broker  # noqa: F401  (puts worker-common on the path)
from testkit import FakeChannel

MARK = "MARK-7f3a-DOCUMENT-TEXT"


def _deliver(job_id):
    import validation_worker

    channel = FakeChannel()
    body = json.dumps({"jobId": job_id, "payload": {}}).encode()
    method = SimpleNamespace(delivery_tag=1, routing_key="job.validation.screen")
    validation_worker._on_message(channel, method, SimpleNamespace(message_id=job_id), body)
    return channel


def test_an_unexpected_failure_carries_the_type_and_the_cause_only(monkeypatch, capsys):
    import validation_worker

    def explode(_payload):
        raise RuntimeError(f"cannot read: {MARK}")

    monkeypatch.setitem(validation_worker.HANDLERS, "job.validation.screen", explode)
    channel = _deliver("job-fault")
    out = capsys.readouterr().out
    (routing_key, event) = channel.published[0]
    assert routing_key == "job.job-fault.error"
    assert event == {"jobId": "job-fault", "error": "RuntimeError", "cause": "EXECUTOR_FAULT"}
    assert MARK not in json.dumps(event)
    assert "cannot read" not in json.dumps(event)
    assert MARK not in out
    assert "cannot read" not in out
    assert "job-fault" in out
    assert "job.validation.screen" in out
    assert "RuntimeError" in out


def test_a_refused_payload_emits_its_coded_reason_and_never_the_input(monkeypatch, capsys):
    import validation_worker

    channel = FakeChannel()
    body = json.dumps({"jobId": "job-bad", "payload": {"jurisdiction": MARK, "document": MARK}}).encode()
    method = SimpleNamespace(delivery_tag=2, routing_key="job.validation.screen")
    validation_worker._on_message(channel, method, SimpleNamespace(message_id="job-bad"), body)
    out = capsys.readouterr().out
    event = channel.published[0][1]
    assert event == {"jobId": "job-bad", "error": "UNKNOWN_JURISDICTION", "cause": "INPUT_INVALID"}
    assert MARK not in json.dumps(event)
    assert MARK not in out
    assert "UNKNOWN_JURISDICTION" in out


def test_a_malformed_json_field_is_refused_without_echoing_what_arrived():
    import pytest
    import validation_worker

    with pytest.raises(validation_worker.InvalidJobPayload) as refused:
        validation_worker._json_field({"resolved": f"{{not json {MARK}"}, "resolved")
    assert refused.value.reason == "FIELD_NOT_JSON"
    assert MARK not in str(refused.value)
    assert MARK not in repr(refused.value)


def test_a_reason_outside_the_closed_set_is_refused():
    import pytest
    import validation_worker

    with pytest.raises(ValueError, match="closed set") as refused:
        validation_worker.InvalidJobPayload(f"free text {MARK}")
    assert MARK not in str(refused.value)
