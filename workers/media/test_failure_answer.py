"""A failed job answers with a coded reason or a type name — never a message that can hold a prompt or a path.

No broker, no GPU: the consumer's callback is driven with ``send_job_error`` replaced, and the speech server's
handler with a fake request. krizaka-internal#3376.
"""

from __future__ import annotations

import io
import json
import types
from contextlib import redirect_stdout
from unittest.mock import MagicMock, patch

import pytest

from app import speech_server
from app.failure import CodedReasonError

MARK = "MARK-8e4f-JOB-TEXT"


def _deliver(consumer, job_id, side_effect):
    """Run one job whose handler raises ``side_effect``: ``(what send_job_error got, what was printed)``."""
    channel = MagicMock()
    method = MagicMock(delivery_tag=1, routing_key="job.video.generate")
    body = json.dumps({"jobId": job_id, "userId": "u-1", "payload": {"prompt": MARK}}).encode()
    out = io.StringIO()
    pika_stub = types.SimpleNamespace(exceptions=types.SimpleNamespace(AMQPError=OSError))
    with (
        patch.object(consumer, "pika", pika_stub),
        patch.object(consumer, "_generate", side_effect=side_effect),
        patch.object(consumer, "send_job_error") as sent,
        redirect_stdout(out),
    ):
        consumer._on_message(channel, method, None, body)
    return sent, out.getvalue()


def test_an_unexpected_failure_carries_the_type_name_only():
    import app.consumer as consumer

    sent, out = _deliver(consumer, "media-fault", RuntimeError(f"cannot read the prompt: {MARK}"))
    sent.assert_called_once()
    assert sent.call_args.args[:2] == ("media-fault", "RuntimeError")
    assert sent.call_args.kwargs["cause"] == consumer.EXECUTOR_FAULT
    assert MARK not in repr(sent.call_args)
    assert MARK not in out
    assert "media-fault" in out
    assert "RuntimeError" in out


def test_a_missing_file_carries_the_type_name_never_its_path():
    import app.consumer as consumer

    sent, out = _deliver(consumer, "media-missing", FileNotFoundError(f"/uploads/u-1/{MARK}/clip.mp4"))
    assert sent.call_args.args[:2] == ("media-missing", "FileNotFoundError")
    assert sent.call_args.kwargs["cause"] == consumer.PLATFORM_UNAVAILABLE
    assert MARK not in repr(sent.call_args)
    assert MARK not in out


def test_a_refusal_carries_its_code_and_never_what_it_was_refused_for():
    import app.consumer as consumer

    refusal = consumer.InvalidJobPayload(consumer.ASPECT_UNSUPPORTED)
    sent, out = _deliver(consumer, "media-bad", _raised(refusal, ValueError(f"unknown aspect '{MARK}'")))
    assert sent.call_args.args[:2] == ("media-bad", "ASPECT_UNSUPPORTED")
    assert sent.call_args.kwargs["cause"] == consumer.INPUT_INVALID
    assert MARK not in repr(sent.call_args)
    assert MARK not in out
    assert "ASPECT_UNSUPPORTED" in out


def _raised(refusal, cause):
    refusal.__cause__ = cause
    return refusal


def test_an_aspect_the_library_refuses_by_quoting_it_leaves_only_as_a_code():
    import app.consumer as consumer

    job = {"jobId": "media-aspect", "userId": "u-1", "payload": {"prompt": "x", "aspect": MARK}}
    with pytest.raises(consumer.InvalidJobPayload) as refused:
        consumer._generate_with(job, "/tmp/scratch-unused")  # noqa: S108 — never reached: the aspect is checked first
    assert refused.value.reason == "ASPECT_UNSUPPORTED"
    assert MARK not in str(refused.value)
    assert MARK not in repr(refused.value)


def test_a_reason_outside_the_closed_set_is_refused():
    import app.consumer as consumer

    for kind in (consumer.InvalidJobPayload, speech_server.SpeechRequestError):
        assert issubclass(kind, CodedReasonError)
        with pytest.raises(ValueError, match="closed set") as refused:
            kind(f"free text {MARK}")
        assert MARK not in str(refused.value)


def test_a_speech_request_is_refused_with_a_code_and_never_the_model_it_named():
    with pytest.raises(speech_server.SpeechRequestError) as refused:
        speech_server.parse_request(json.dumps({"model": MARK, "input": "x"}).encode())
    assert refused.value.reason == "MODEL_NOT_SERVED"
    assert MARK not in str(refused.value)


def _speech_handler(text):
    handler = object.__new__(speech_server.Handler)
    body = json.dumps({"input": text}).encode()
    handler.headers = {"Content-Length": str(len(body))}
    handler.rfile = io.BytesIO(body)
    handler.sent = []
    handler._send = lambda code, payload, content_type, extra=None: handler.sent.append((code, payload))
    return handler


def test_a_speech_failure_answers_with_the_type_name_never_the_text_it_was_reading():
    handler = _speech_handler(MARK)
    with patch.object(speech_server, "synthesize", side_effect=RuntimeError(f"cannot read {MARK}")):
        handler._speech()
    (code, payload) = handler.sent[0]
    assert code == 500
    assert json.loads(payload) == {"error": "speech failed: RuntimeError"}
    assert MARK.encode() not in payload


def test_an_asset_sink_failure_carries_its_message_which_names_the_cause():
    import app.consumer as consumer
    from app import asset_sink

    refused = asset_sink.AssetSinkError("the job service refused the upload (400)")
    sent, out = _deliver(consumer, "media-sink", refused)
    assert sent.call_args.args[:2] == ("media-sink", "AssetSinkError: the job service refused the upload (400)")
    assert sent.call_args.kwargs["cause"] == consumer.EXECUTOR_FAULT
    assert "the job service refused the upload (400)" in out
