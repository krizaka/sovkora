"""A failed job answers with a type name or a coded reason — never a message that can hold document text.
Run: python3 -m pytest test_failure_answer.py -q. A fake channel, no broker."""

import json
import os
import sys
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

MARK = "MARK-7f3a-DOCUMENT-TEXT"


class FakeChannel:
    def __init__(self):
        self.published, self.acked = [], []

    def basic_publish(self, exchange, routing_key, body, properties):
        self.published.append((routing_key, json.loads(body)))

    def basic_ack(self, delivery_tag):
        self.acked.append(delivery_tag)


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
    assert MARK not in json.dumps(event) and "cannot read" not in json.dumps(event)
    assert MARK not in out and "cannot read" not in out
    assert "job-fault" in out and "job.validation.screen" in out and "RuntimeError" in out


def test_a_refused_payload_emits_its_coded_reason_and_never_the_input(monkeypatch, capsys):
    import validation_worker

    channel = FakeChannel()
    body = json.dumps({"jobId": "job-bad", "payload": {"jurisdiction": MARK, "document": MARK}}).encode()
    method = SimpleNamespace(delivery_tag=2, routing_key="job.validation.screen")
    validation_worker._on_message(channel, method, SimpleNamespace(message_id="job-bad"), body)
    out = capsys.readouterr().out
    event = channel.published[0][1]
    assert event == {"jobId": "job-bad", "error": "UNKNOWN_JURISDICTION", "cause": "INPUT_INVALID"}
    assert MARK not in json.dumps(event) and MARK not in out
    assert "UNKNOWN_JURISDICTION" in out


def test_a_malformed_json_field_is_refused_without_echoing_what_arrived(capsys):
    import validation_worker

    try:
        validation_worker._json_field({"resolved": f"{{not json {MARK}"}, "resolved")
    except validation_worker.InvalidJobPayload as bad:
        assert bad.reason == "FIELD_NOT_JSON"
        assert MARK not in str(bad) and MARK not in repr(bad)
        return
    raise AssertionError("a malformed JSON field must be refused")


def test_a_reason_outside_the_closed_set_is_refused():
    import validation_worker

    try:
        validation_worker.InvalidJobPayload(f"free text {MARK}")
    except ValueError as bad:
        assert MARK not in str(bad)
        return
    raise AssertionError("InvalidJobPayload takes a code from the closed set, never free text")
