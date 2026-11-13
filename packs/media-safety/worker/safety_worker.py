#!/usr/bin/env python3
"""The media-safety worker: the deterministic half of a safety check. The two model steps are the blueprint's (metered like any step)."""

import json
import os

import broker  # noqa: F401  (puts worker-common on the path)
import rules
import worker_failure
import worker_loop
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
DECLARATION = yaml.safe_load(open(os.path.join(HERE, "worker.yaml"), encoding="utf-8"))
FRAMES_OUT_OF_RANGE, EXPECTS_SPEECH_NOT_BOOLEAN = "FRAMES_OUT_OF_RANGE", "EXPECTS_SPEECH_NOT_BOOLEAN"


class InvalidJobPayload(worker_failure.InvalidJobPayload):
    """The payload was checked and is unusable — the one cause that bills. Its reason is a code, never a value."""

    REASONS = frozenset({FRAMES_OUT_OF_RANGE, EXPECTS_SPEECH_NOT_BOOLEAN})


def _assess(payload: dict) -> dict:
    frames = payload.get("frames")
    if isinstance(frames, str) and frames.strip().isdigit():  # a blueprint renders a lone number as text
        frames = int(frames)
    if isinstance(frames, bool) or not isinstance(frames, int) or not 0 <= frames <= 8:
        raise InvalidJobPayload(FRAMES_OUT_OF_RANGE)
    expects_speech = payload.get("expectsSpeech")
    if isinstance(expects_speech, str) and expects_speech.strip().lower() in ("true", "false"):
        expects_speech = expects_speech.strip().lower() == "true"
    if not isinstance(expects_speech, bool):
        raise InvalidJobPayload(EXPECTS_SPEECH_NOT_BOOLEAN)
    report = rules.assess(rules.load_ruleset(), payload.get("looks"), frames, payload.get("heard"), expects_speech)
    return {"report": report, "reportJson": json.dumps(report), "verdict": report["verdict"]}


HANDLERS = {"job.safety.assess": _assess}
handle = worker_loop.make_handle(HANDLERS)


def main() -> None:
    worker_loop.run("safety", DECLARATION, handle)


if __name__ == "__main__":
    main()
