#!/usr/bin/env python3
"""The identity-document worker: the deterministic half of an identity-document check.

Tier-W, like the other pack workers: it links no Orazaka library and speaks only AMQP. One routing key around one model
call that the blueprint makes (so the inference is metered like any other — this worker calls no model):

``job.identity.verify``
    Takes what the vision step *read* off the document (the zone's lines, the printed fields, the signs of tampering) and
    decides what can be decided by arithmetic: the check digits, the calendar, the agreement between the printed fields,
    the zone and the barcode (:mod:`rules`, :mod:`mrz`). It never receives the image and keeps nothing.
"""

import datetime as dt
import json
import os
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "worker-common"))
import worker_failure  # noqa: E402
import worker_loop  # noqa: E402

import rules  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
MAX_BARCODE_CHARS = 20000

AS_OF_NOT_A_DAY = "AS_OF_NOT_A_DAY"
AS_OF_NOT_REAL = "AS_OF_NOT_REAL"
MINIMUM_AGE_OUT_OF_RANGE = "MINIMUM_AGE_OUT_OF_RANGE"
READING_INVALID = "READING_INVALID"
BARCODE_TOO_LONG = "BARCODE_TOO_LONG"


class InvalidJobPayload(worker_failure.InvalidJobPayload):
    """The payload was checked and is unusable — the one cause that bills. Its reason is a code, never a value."""

    REASONS = frozenset({AS_OF_NOT_A_DAY, AS_OF_NOT_REAL, MINIMUM_AGE_OUT_OF_RANGE, READING_INVALID, BARCODE_TOO_LONG})


def today() -> str:
    return dt.date.today().isoformat()


def _as_of(payload: dict) -> str:
    value = payload.get("asOf")
    if value in (None, ""):
        return today()
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise InvalidJobPayload(AS_OF_NOT_A_DAY)
    try:
        dt.date.fromisoformat(value)
    except ValueError as bad:
        raise InvalidJobPayload(AS_OF_NOT_REAL) from bad
    return value


def _minimum_age(payload: dict):
    value = payload.get("minimumAge")
    if value in (None, ""):
        return None
    if isinstance(value, str) and value.strip().isdigit():  # a blueprint renders a lone number as text
        value = int(value.strip())
    if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 120:
        raise InvalidJobPayload(MINIMUM_AGE_OUT_OF_RANGE)
    return value


def _verify(payload: dict) -> dict:
    try:
        reading = rules.parse_reading(payload.get("reading"))
    except rules.InvalidReading as bad:
        raise InvalidJobPayload(READING_INVALID) from bad
    barcode = payload.get("barcodeText") or ""
    if not isinstance(barcode, str) or len(barcode) > MAX_BARCODE_CHARS:
        raise InvalidJobPayload(BARCODE_TOO_LONG)
    as_of = _as_of(payload)
    try:
        report = rules.assess(
            rules.load_ruleset(), reading, as_of, minimum_age=_minimum_age(payload), barcode_text=barcode
        )
    except rules.InvalidReading as bad:
        raise InvalidJobPayload(READING_INVALID) from bad
    return {"report": report, "reportJson": json.dumps(report), "status": report["status"]}


handle = worker_loop.make_handle({"job.identity.verify": _verify})


def main() -> None:
    worker_loop.run("identity", yaml.safe_load(open(os.path.join(HERE, "worker.yaml"), encoding="utf-8")), handle)


if __name__ == "__main__":
    main()
