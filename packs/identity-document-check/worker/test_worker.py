#!/usr/bin/env python3
"""The identity worker: the payload checks and the handler, without a broker. Run: python3 test_worker.py"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "worker-common"))
import testkit  # noqa: E402
import identity_worker as worker  # noqa: E402
import worker_loop  # noqa: E402
from worker_failure import failure_answer  # noqa: E402

InvalidJobPayload = worker.InvalidJobPayload

TD3 = ["P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10"]
READING = {
    "documentType": "PASSPORT",
    "mrzLines": TD3,
    "printed": {},
    "signals": [],
    "quality": {},
    "confidence": {"mrz": 0.9},
}


def _verify(**payload):
    return worker._verify(dict({"reading": json.dumps(READING), "asOf": "2011-06-01"}, **payload))


def test_the_routing_key_chooses_the_work_and_an_unknown_one_is_refused():
    result, consumption = worker.handle(
        "job.identity.verify", {"payload": {"reading": json.dumps(READING), "asOf": "2011-06-01"}}
    )
    assert result["report"]["status"] == "CONSISTENT" and result["status"] == "CONSISTENT"
    assert consumption["tokens"] == 0
    try:
        worker.handle("job.identity.other", {"payload": {}})
    except ValueError:
        return
    raise AssertionError("an unknown routing key must be refused")


def test_the_report_comes_back_as_an_object_and_as_json_for_the_next_step():
    result = _verify()
    assert json.loads(result["reportJson"]) == result["report"]


def test_numbers_and_flags_arrive_as_text_from_a_blueprint_and_are_read_as_what_they_stand_for():
    result = _verify(minimumAge="40")
    assert result["report"]["age"]["minimum"] == 40 and result["status"] == "REVIEW"
    assert _verify(minimumAge="")["report"]["age"]["minimum"] is None


def test_a_blank_reference_day_means_today_and_a_wrong_one_is_refused():
    assert _verify(asOf="")["report"]["asOf"] == worker.today()
    for bad in ("2026-13-01", "yesterday", 20260101):
        try:
            _verify(asOf=bad)
        except InvalidJobPayload:
            continue
        raise AssertionError(f"{bad!r} was not refused")


def test_an_unusable_payload_is_refused_as_input_never_as_a_fault():
    for payload in (
        {},
        {"reading": "no json here"},
        {"reading": "[]"},
        {"reading": json.dumps(READING), "minimumAge": "abc"},
        {"reading": json.dumps(READING), "minimumAge": "150"},
    ):
        try:
            worker._verify(dict({"asOf": "2011-06-01"}, **payload))
        except InvalidJobPayload:
            continue
        raise AssertionError(f"{payload!r} was not refused")


def test_the_barcode_text_is_optional_and_bounded():
    assert _verify(barcodeText="")["report"]["barcode"]["present"] is False
    try:
        _verify(barcodeText="x" * 20001)
    except InvalidJobPayload:
        return
    raise AssertionError("an oversized barcode text must be refused")


def _deliver(job_id, payload):
    on_message = worker_loop.make_on_message("identity", worker.handle, set())
    return testkit.deliver(on_message, "job.identity.verify", {"jobId": job_id, "payload": payload})


def test_a_failure_carries_the_type_name_only_never_the_text_of_the_exception():
    def explode(payload):
        raise RuntimeError(f"cannot read: {testkit.MARK}")

    handle = worker_loop.make_handle({"job.identity.verify": explode})
    on_message = worker_loop.make_on_message("identity", handle, set())
    channel, out = testkit.deliver(on_message, "job.identity.verify", {"jobId": "id-fault", "payload": {}})
    testkit.assert_failure_leaks_nothing(channel, out, "RuntimeError", "EXECUTOR_FAULT", "id-fault")


def test_a_refused_payload_answers_with_a_code_and_never_the_value_that_was_refused():
    channel, out = _deliver("id-asof", {"reading": json.dumps(READING), "asOf": testkit.MARK})
    testkit.assert_failure_leaks_nothing(channel, out, "AS_OF_NOT_A_DAY", "INPUT_INVALID", "id-asof")
    channel, out = _deliver("id-reading", {"reading": f"{{not json {testkit.MARK}", "asOf": "2011-06-01"})
    testkit.assert_failure_leaks_nothing(channel, out, "READING_INVALID", "INPUT_INVALID", "id-reading")
    channel, out = _deliver("id-age", {"reading": json.dumps(READING), "minimumAge": testkit.MARK})
    testkit.assert_failure_leaks_nothing(channel, out, "MINIMUM_AGE_OUT_OF_RANGE", "INPUT_INVALID", "id-age")
    channel, out = _deliver("id-code", {"reading": json.dumps(READING), "barcodeText": testkit.MARK * 20000})
    testkit.assert_failure_leaks_nothing(channel, out, "BARCODE_TOO_LONG", "INPUT_INVALID", "id-code")


def test_a_reason_outside_the_closed_set_is_refused():
    testkit.assert_free_text_reason_refused(InvalidJobPayload)
    assert failure_answer("j", InvalidJobPayload("READING_INVALID"))["error"] == "READING_INVALID"


if __name__ == "__main__":
    testkit.main(globals())
