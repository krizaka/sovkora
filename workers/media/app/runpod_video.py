"""Runpod Public Endpoints behind the provider port: a video model this worker routes to, not one it runs.

API (docs.runpod.io, "Public Endpoints"): ``POST {base}/{endpoint}/run`` with an ``input`` object answers a job
id, ``GET {base}/{endpoint}/status/{id}`` its status (``IN_QUEUE``, ``IN_PROGRESS``, ``COMPLETED``,
``FAILED``…), and a completed job's ``output.result`` is the https URL of the MP4 (valid 7 days) and
``output.cost`` its price in USD. The endpoint id is the catalogue row's ``remoteModel``; everything a model
needs beyond the prompt, the seed, the duration, the aspect and the starting frame is the row's ``providerInput``.

Configuration, by bounded context (never a vendor-named variable beyond the GPU provider's own): the key
``AI_GPU_RUNPOD_API_KEY`` (never logged, never in a message) and, optionally, ``AI_GPU_RUNPOD_API_URL``.

The price is ``costUsd`` **per second** of clip × the seconds asked, reserved in :mod:`app.spend` before the
call. §12 Zero-Fallback: a refusal, a failed job, a timeout or a non-https link raises; no placeholder clip.
"""

from __future__ import annotations

import base64
import json
import os
import time
import urllib.parse
import urllib.request

from app import spend
from app.remote_video import DEFAULT_TIMEOUT_SECONDS, POLL_SECONDS, RemoteVideoError, _call
from app.video_models import InvalidVideoRequest, VideoModel

#: The provider's name in the catalogue (``ai_providers``) and in ``models.yaml``.
PROVIDER = "runpod-public-endpoint"
DEFAULT_URL = "https://api.runpod.ai/v2"
KEY_VARIABLE = "AI_GPU_RUNPOD_API_KEY"

_FAILED = ("FAILED", "CANCELLED", "TIMED_OUT")


def api_key(env: dict | None = None) -> str:
    source = os.environ if env is None else env
    key = (source.get(KEY_VARIABLE) or "").strip()
    if not key:
        raise RemoteVideoError(f"{KEY_VARIABLE} is not set: this model is served by a provider and needs its key")
    return key


def base_url(env: dict | None = None) -> str:
    source = os.environ if env is None else env
    return (source.get("AI_GPU_RUNPOD_API_URL") or DEFAULT_URL).strip().rstrip("/")


def aspect_of(model: VideoModel, width: int, height: int) -> str | None:
    """The ratio the model declares for this size (``sizes`` of its row), or None for an explicit odd size."""
    return next((ratio for ratio, size in model.sizes.items() if tuple(size) == (width, height)), None)


def _data_uri(image: bytes) -> str:
    return "data:image/png;base64," + base64.b64encode(image).decode("ascii")


def request_input(
    model: VideoModel, prompt: str, width: int, height: int, seed: int | None, seconds: int, image: bytes | None
) -> dict:
    """The ``input`` object: the row's fixed fields first, then what this clip asks."""
    body = dict(model.provider_input)
    body["prompt"] = prompt
    body["duration"] = seconds
    ratio = aspect_of(model, width, height)
    if ratio:
        body["aspect_ratio"] = ratio
    if image is not None:
        body["image"] = _data_uri(image)
    if seed is not None and seed >= 0:
        body["seed"] = int(seed)
    return body


def _seconds(model: VideoModel, seconds: int | None) -> int:
    low, high = model.min_seconds or 1, model.max_seconds or 30
    asked = low if seconds is None else int(seconds)
    if not low <= asked <= high:
        raise InvalidVideoRequest(f"model '{model.name}' makes {low} to {high} seconds, not {asked}")
    return asked


def _download(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=300) as response:  # noqa: S310 — the provider's https URL, checked by the caller
        return response.read()


def _without_key(call, key: str):
    """``call`` with the key scrubbed from any refusal it raises: a provider that echoes it never leaks it."""

    def guarded(*args, **kwargs):
        try:
            return call(*args, **kwargs)
        except RemoteVideoError as refused:
            raise RemoteVideoError(str(refused).replace(key, "***")) from None

    return guarded


class RunpodVideoProvider:
    """Runpod Public Endpoints: text-to-video and image-to-video, priced per second of clip."""

    def generate(
        self,
        model: VideoModel,
        prompt: str,
        width: int,
        height: int,
        seed: int | None,
        progress=None,
        *,
        image: bytes | None = None,
        seconds: int | None = None,
        env: dict | None = None,
        sleep=time.sleep,
        clock=time.monotonic,
        call=_call,
        download=_download,
    ) -> tuple[bytes, dict]:
        key = api_key(env)
        call = _without_key(call, key)
        if not model.remote_model:
            raise RemoteVideoError(f"model '{model.name}' declares no endpoint id (remoteModel)")
        asked = _seconds(model, seconds)
        cost = round((model.cost_usd or 0.0) * asked, 4)
        try:
            spend.reserve(cost, env)
        except spend.SpendRefused as refused:
            raise RemoteVideoError(str(refused)) from refused
        endpoint = f"{base_url(env)}/{urllib.parse.quote(model.remote_model, safe='')}"
        started = clock()
        job = self._json(
            call(
                "POST",
                f"{endpoint}/run",
                key,
                {"input": request_input(model, prompt, width, height, seed, asked, image)},
            )
        )
        job_id = job.get("id")
        if not job_id:
            raise RemoteVideoError("the provider accepted the request but returned no job id")
        timeout = int(
            (os.environ if env is None else env).get("MEDIA_REMOTE_TIMEOUT_SECONDS") or DEFAULT_TIMEOUT_SECONDS
        )
        result = self._wait(
            job, str(job_id), f"{endpoint}/status/{job_id}", timeout, started, clock, sleep, progress, key, call
        )
        output = result.get("output") if isinstance(result.get("output"), dict) else {}
        url = output.get("result")
        if not isinstance(url, str) or urllib.parse.urlsplit(url).scheme != "https":
            raise RemoteVideoError(f"the provider's job {job_id} gave no https download link")
        video = download(url)
        if not video:
            raise RemoteVideoError(f"the provider's job {job_id} returned an empty file")
        reported = output.get("cost")
        billed = float(reported) if isinstance(reported, (int, float)) and not isinstance(reported, bool) else cost
        return video, {
            "provider": model.provider or PROVIDER,
            "providerJobId": str(job_id),
            "providerSeconds": round(clock() - started, 1),
            "costUsd": billed,
        }

    @staticmethod
    def _json(answer: tuple[int, bytes]) -> dict:
        try:
            parsed = json.loads(answer[1].decode("utf-8") or "{}")
        except ValueError as unreadable:
            raise RemoteVideoError("the provider answered something that is not JSON") from unreadable
        if not isinstance(parsed, dict):
            raise RemoteVideoError("the provider answered something that is not a JSON object")
        return parsed

    def _wait(self, job, job_id, status_url, timeout, started, clock, sleep, progress, key, call) -> dict:
        result = job
        while result.get("status") != "COMPLETED":
            if result.get("status") in _FAILED:
                raise RemoteVideoError(
                    f"the provider's job {job_id} ended as {result['status']}: {str(result.get('error') or '')[:300]}"
                )
            if clock() - started > timeout:
                raise RemoteVideoError(f"the provider's job {job_id} did not finish within {timeout}s")
            sleep(POLL_SECONDS)
            result = self._json(call("GET", status_url, key))
            if progress:
                progress(min(95, int((clock() - started) / max(timeout, 1) * 100)))
        return result
