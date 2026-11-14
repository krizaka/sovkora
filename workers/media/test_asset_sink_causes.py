"""The hand-over to the job service says which cause failed, and the pod's service token has a functional name.

krizaka-internal#3619: a RunPod video run lost its clip at the hand-over and the error event carried only a class name.
No network: ``urlopen`` is replaced.
"""

from __future__ import annotations

import io
import os
import urllib.error
from unittest.mock import patch

import pytest

from app import asset_sink, registration

SINK = {"MEDIA_ASSET_SINK_URL": "http://x"}
JOB = {"jobId": "job-9", "userId": "user-1", "featureKey": "video", "dataClass": "STANDARD"}


@pytest.fixture
def clip(tmp_path):
    path = tmp_path / "video.mp4"
    path.write_bytes(b"clip")
    return str(path)


def _refusing(status):
    return urllib.error.HTTPError("http://x", status, "refused", {}, io.BytesIO(b"{}"))


@pytest.mark.parametrize(
    ("status", "cause"),
    [
        (400, asset_sink.UPLOAD_REJECTED),
        (401, asset_sink.UPLOAD_UNAUTHORIZED),
        (403, asset_sink.UPLOAD_UNAUTHORIZED),
        (503, asset_sink.UPLOAD_REFUSED),
    ],
)
def test_a_refused_upload_says_which_cause_by_its_status(clip, status, cause):
    with (
        patch.object(asset_sink, "_service_token", return_value="tok"),
        patch.object(asset_sink.urllib.request, "urlopen", side_effect=_refusing(status)),
        pytest.raises(asset_sink.AssetSinkError) as raised,
    ):
        asset_sink.upload(clip, JOB, "video.mp4", "video/mp4", SINK)
    assert raised.value.reason == cause


def test_an_unconfigured_sink_and_a_missing_token_are_told_apart(clip):
    with pytest.raises(asset_sink.AssetSinkError) as unset:
        asset_sink.upload(clip, JOB, "video.mp4", "video/mp4", {})
    assert unset.value.reason == asset_sink.SINK_URL_NOT_SET
    with (
        patch.object(asset_sink, "_service_token", return_value=None),
        pytest.raises(asset_sink.AssetSinkError) as no_token,
    ):
        asset_sink.upload(clip, JOB, "video.mp4", "video/mp4", SINK)
    assert no_token.value.reason == asset_sink.SERVICE_TOKEN_MISSING


def test_a_starting_image_the_job_service_does_not_find_is_a_cause_of_its_own(tmp_path):
    with (
        patch.object(asset_sink, "_service_token", return_value="tok"),
        patch.object(asset_sink.urllib.request, "urlopen", side_effect=_refusing(404)),
        pytest.raises(asset_sink.AssetSinkError) as raised,
    ):
        asset_sink.fetch_image(JOB, str(tmp_path / "in"), SINK)
    assert raised.value.reason == asset_sink.INPUT_NOT_FOUND


def test_the_worker_names_the_actor_with_the_headers_the_job_service_reads(clip):
    seen = {}

    class Reply:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def read(self):
            return b'{"url": "/api/v1/assets/job-9/video.mp4"}'

    def fake_urlopen(request, timeout):
        seen.update({k.lower(): v for k, v in request.header_items()})
        return Reply()

    with (
        patch.object(asset_sink, "_service_token", return_value="tok"),
        patch.object(asset_sink.urllib.request, "urlopen", fake_urlopen),
    ):
        asset_sink.upload(clip, JOB, "video.mp4", "video/mp4", SINK)
    # JobOutputController reads X-Sovkora-User-Id / -Feature-Key / -Data-Class; a worker sending any other
    # name leaves the service with no actor (a 400 on the upload, a 404 on the starting image).
    assert seen["x-sovkora-user-id"] == "user-1"
    assert seen["x-sovkora-feature-key"] == "video"
    assert seen["x-sovkora-data-class"] == "STANDARD"


def test_the_service_token_is_read_from_its_functional_name_then_the_retired_one():
    kept = {k: v for k, v in os.environ.items() if not k.endswith("_SERVICE_TOKEN")}
    with patch.dict(os.environ, {**kept, "SOVKORA_SERVICE_TOKEN": "new", "ORAZAKA_SERVICE_TOKEN": "old"}, clear=True):
        assert registration._service_token() == "new"
    with patch.dict(os.environ, {**kept, "ORAZAKA_SERVICE_TOKEN": "old"}, clear=True):
        assert registration._service_token() == "old"


def test_a_gpu_pods_own_token_wins_over_any_service_credential():
    # krizaka-internal#3645: a RunPod pod holds only the job-scoped token the job service minted for it;
    # when it is there, the worker never presents (nor mints) a SERVICE token.
    kept = {k: v for k, v in os.environ.items() if not k.endswith("_SERVICE_TOKEN") and k != "SOVKORA_POD_TOKEN"}
    env = {**kept, "SOVKORA_POD_TOKEN": "pod", "SOVKORA_SERVICE_TOKEN": "svc", "IDENTITY_JWT_SECRET": "s" * 40}
    with patch.dict(os.environ, env, clear=True):
        assert registration._service_token() == "pod"
