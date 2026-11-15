"""Runpod Public Endpoints behind the provider port: what is asked, how it ends, how it fails (recorded answers, no network)."""

from __future__ import annotations

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from app import main, remote_providers, remote_video, runpod_video, video_models

MODEL = "seedance-2-5-i2v-480p"
KEY = "rp-secret-key"
ENV = {
    "AI_GPU_RUNPOD_API_KEY": KEY,
    "AI_GPU_RUNPOD_API_URL": "https://runpod.test/v2",
    "AI_VIDEO_DAILY_RUNS": "-1",
    "AI_SPEND_DAILY_BUDGET_USD": "-1",
}
URL = "https://video.runpod.ai/abc/output.mp4"


def recorder(answers):
    """The transport of a test: answers in order (an exception is raised), every call kept in ``.calls``."""
    pending, calls = list(answers), []

    def call(method, url, key, body=None, timeout=60):
        calls.append((method, url, key, body))
        answer = pending.pop(0)
        if isinstance(answer, Exception):
            raise answer
        return 200, json.dumps(answer).encode()

    call.calls = calls
    return call


def run(answers, *, seconds=4, image=b"PNG", env=ENV, clock=None, download=lambda url: b"MP4", model=MODEL):
    call = recorder(answers)
    extra = {"clock": clock} if clock else {}
    result = runpod_video.RunpodVideoProvider().generate(
        video_models.find(model),
        "the boss turns",
        480,
        854,
        7,
        image=image,
        seconds=seconds,
        env=env,
        sleep=lambda s: None,
        call=call,
        download=download,
        **extra,
    )
    return result, call


class TestCatalogue(unittest.TestCase):
    def test_the_rows_are_runpod_models_priced_per_second_with_a_source(self):
        for name, price, size in (
            ("seedance-2-5-i2v-480p", 0.18, (480, 854)),
            ("seedance-2-5-i2v-1080p", 0.90, (1080, 1920)),
        ):
            model = video_models.find(name)
            self.assertEqual(
                ("remote", "runpod-public-endpoint", "16jsvx5ncmm24x"),
                (model.pipeline, model.provider, model.remote_model),
            )
            self.assertEqual(price, model.cost_usd)
            self.assertEqual(size, model.size_for("9:16"))
            self.assertTrue(model.image_to_video and model.requires_image)
            self.assertEqual((4, 30), (model.min_seconds, model.max_seconds))
            self.assertEqual("Remote", main.MODEL_REGISTRY[name])

    def test_the_provider_is_chosen_by_the_models_provider(self):
        self.assertIsInstance(remote_providers.provider_for(video_models.find(MODEL)), runpod_video.RunpodVideoProvider)
        self.assertIsInstance(
            remote_providers.provider_for(video_models.find("wan2-2-t2v-a14b")), remote_video.DigitalOceanVideoProvider
        )

    def test_an_unknown_provider_is_refused_by_name(self):
        unknown = video_models.find(MODEL).__class__(**{**video_models.find(MODEL).__dict__, "provider": "nobody"})
        with self.assertRaisesRegex(video_models.InvalidVideoRequest, "nobody"):
            remote_providers.provider_for(unknown)


class TestGenerate(unittest.TestCase):
    def test_submits_polls_and_downloads(self):
        (video, facts), call = run(
            [
                {"id": "job-1", "status": "IN_QUEUE"},
                {"id": "job-1", "status": "IN_PROGRESS"},
                {"id": "job-1", "status": "COMPLETED", "output": {"result": URL, "cost": 0.72}},
            ]
        )
        self.assertEqual(b"MP4", video)
        method, url, key, body = call.calls[0]
        self.assertEqual(("POST", "https://runpod.test/v2/16jsvx5ncmm24x/run", KEY), (method, url, key))
        sent = body["input"]
        self.assertEqual(
            {
                "resolution": "480p",
                "generate_audio": False,
                "prompt": "the boss turns",
                "duration": 4,
                "aspect_ratio": "9:16",
                "seed": 7,
            },
            {k: v for k, v in sent.items() if k != "image"},
        )
        self.assertTrue(sent["image"].startswith("data:image/png;base64,"))
        self.assertEqual("https://runpod.test/v2/16jsvx5ncmm24x/status/job-1", call.calls[1][1])
        self.assertEqual(
            {"provider": "runpod-public-endpoint", "providerJobId": "job-1", "costUsd": 0.72},
            {k: facts[k] for k in ("provider", "providerJobId", "costUsd")},
        )

    def test_a_job_already_completed_on_submit_is_downloaded_without_polling(self):
        _, call = run([{"id": "j", "status": "COMPLETED", "output": {"result": URL}}])
        self.assertEqual(1, len(call.calls))

    def test_a_failed_job_raises(self):
        with self.assertRaisesRegex(remote_video.RemoteVideoError, "FAILED: nsfw"):
            run([{"id": "j", "status": "IN_QUEUE"}, {"id": "j", "status": "FAILED", "error": "nsfw"}])

    def test_a_job_that_never_ends_times_out(self):
        ticks = iter(range(0, 100000, 400))
        with self.assertRaisesRegex(remote_video.RemoteVideoError, "did not finish"):
            run([{"id": "j", "status": "IN_PROGRESS"}] * 10, clock=lambda: next(ticks))

    def test_a_download_link_that_is_not_https_is_never_opened(self):
        opened = []
        with self.assertRaisesRegex(remote_video.RemoteVideoError, "https"):
            run(
                [{"id": "j", "status": "COMPLETED", "output": {"result": "http://video.runpod.ai/x.mp4"}}],
                download=lambda url: opened.append(url) or b"x",
            )
        self.assertEqual([], opened)

    def test_a_completed_job_without_a_link_is_refused(self):
        with self.assertRaisesRegex(remote_video.RemoteVideoError, "no https download link"):
            run([{"id": "j", "status": "COMPLETED", "output": {}}])

    def test_a_missing_key_is_refused_before_any_call_and_names_the_variable_only(self):
        with self.assertRaisesRegex(remote_video.RemoteVideoError, "AI_GPU_RUNPOD_API_KEY is not set"):
            run([], env={})

    def test_the_key_is_never_in_a_refusal(self):
        refusal = remote_video.RemoteVideoError(f"the provider answered 401: bad key {KEY}")
        with self.assertRaises(remote_video.RemoteVideoError) as caught:
            run([refusal])
        self.assertNotIn(KEY, str(caught.exception))

    def test_the_duration_is_reserved_at_the_price_per_second_before_the_call(self):
        reserved = []
        with patch("app.spend.reserve", side_effect=lambda cost, env=None: reserved.append(cost)):
            run([{"id": "j", "status": "COMPLETED", "output": {"result": URL}}], seconds=4)
        self.assertEqual([0.72], reserved)

    def test_a_refused_reservation_fails_the_job_before_the_provider_is_called(self):
        with tempfile.TemporaryDirectory() as root:
            env = {**ENV, "AI_SPEND_STATE_FILE": os.path.join(root, "s.json"), "AI_SPEND_DAILY_BUDGET_USD": "0.5"}
            call_made = recorder([])
            with self.assertRaisesRegex(remote_video.RemoteVideoError, "daily budget"):
                runpod_video.RunpodVideoProvider().generate(
                    video_models.find(MODEL), "x", 480, 854, None, image=b"P", seconds=4, env=env, call=call_made
                )
            self.assertEqual([], call_made.calls)

    def test_a_duration_outside_the_models_range_is_refused(self):
        for seconds in (3, 31):
            with self.assertRaisesRegex(video_models.InvalidVideoRequest, "4 to 30"):
                run([], seconds=seconds)

    def test_no_duration_means_the_models_shortest(self):
        _, call = run([{"id": "j", "status": "COMPLETED", "output": {"result": URL}}], seconds=None)
        self.assertEqual(4, call.calls[0][3]["input"]["duration"])


class TestRun(unittest.TestCase):
    def _png(self, root):
        from PIL import Image

        path = os.path.join(root, "key.png")
        Image.new("RGB", (8, 8), (10, 20, 30)).save(path)
        return path

    def test_a_starting_image_is_accepted_for_an_image_to_video_model(self):
        seen = {}

        def fake(model, prompt, w, h, seed, progress=None, image=None, seconds=None, **_):
            seen.update(image=image, seconds=seconds, size=(w, h))
            return b"VIDEO", {"provider": "runpod-public-endpoint", "providerJobId": "j", "costUsd": 0.72}

        with tempfile.TemporaryDirectory() as root, patch.dict(os.environ, {"UPLOAD_DIR": root, "TMPDIR": root}):
            out = os.path.join(root, "u", "j", "output", "video.mp4")
            body = main.run_remote(
                {
                    "prompt": "x",
                    "aspect": "9:16",
                    "durationSeconds": 4,
                    "image_path": self._png(root),
                    "output_path": out,
                },
                MODEL,
                generate=fake,
            )
            self.assertEqual(b"VIDEO", open(out, "rb").read())
        self.assertTrue(seen["image"].startswith(b"\x89PNG"))
        self.assertEqual((4, (480, 854)), (seen["seconds"], seen["size"]))
        self.assertEqual("runpod-public-endpoint", body["metrics"]["provider"])

    def test_a_starting_image_is_refused_for_a_text_only_model(self):
        with self.assertRaisesRegex(video_models.InvalidVideoRequest, "text only"):
            main.run_remote({"prompt": "x", "image": "abc"}, "wan2-2-t2v-a14b", generate=lambda *a, **k: (b"", {}))

    def test_a_model_that_needs_a_photo_refuses_a_text_only_job(self):
        with self.assertRaisesRegex(video_models.InvalidVideoRequest, "starts from a photo"):
            main.run_remote({"prompt": "x"}, MODEL, generate=lambda *a, **k: (b"", {}))

    def test_an_unreadable_starting_image_is_refused_by_name(self):
        with self.assertRaisesRegex(video_models.InvalidVideoRequest, "cannot be read"):
            main.run_remote({"prompt": "x", "image": "not-base64-image"}, MODEL, generate=lambda *a, **k: (b"", {}))

    def test_a_duration_that_is_not_a_number_is_refused(self):
        with self.assertRaisesRegex(video_models.InvalidVideoRequest, "not a number"):
            main._whole_seconds({"durationSeconds": "soon"})
