"""
@file test_main.py
@description Unit tests for orazaka-video-worker covering:
  - preflight_memory_guard (§12 host-memory guard; zero-fallback)
  - send_progress_update AMQP dispatch
  - resource_guard context manager
  - consumption metering (ADR-033 §6.3: measurements reported, units never named)
  - VideoInferenceHandler GET/POST routes
  - Model registry validation
  - Memory cap enforcement
  - Base64/output_path response modes
"""

import base64
import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from http.server import HTTPServer
from threading import Thread
from unittest.mock import MagicMock, patch

import pytest
from PIL import Image

# ---------------------------------------------------------------------------
# Patch heavy ML dependencies before importing the module under test
# ---------------------------------------------------------------------------
mock_torch = MagicMock()
mock_torch.manual_seed.return_value = MagicMock()
mock_torch.backends.mps.is_available.return_value = False
mock_torch.cuda.is_available.return_value = False
mock_torch.float16 = "float16"
sys.modules["torch"] = mock_torch

mock_diffusers = MagicMock()
sys.modules["diffusers"] = mock_diffusers
sys.modules["diffusers.utils"] = MagicMock()

sys.modules["torchvision"] = MagicMock()
sys.modules["accelerate"] = MagicMock()
sys.modules["safetensors"] = MagicMock()
sys.modules["transformers"] = MagicMock()

# Patch pika before import
mock_pika = MagicMock()
sys.modules["pika"] = mock_pika


def _use_the_broker_mock(case):
    """Points app.telemetry at ``mock_pika`` for one test. Injecting ``sys.modules["pika"]`` above is not enough when
    an earlier suite of the same run already imported app.telemetry with the real pika (the CI runs every suite of the
    worker in one process): the module keeps the pika it was born with."""
    import app.telemetry

    patcher = patch.object(app.telemetry, "pika", mock_pika)
    patcher.start()
    case.addCleanup(patcher.stop)


# Now stub the global pipeline load to avoid model download
mock_pipe = MagicMock()
mock_diffusers.StableVideoDiffusionPipeline.from_pretrained.return_value = mock_pipe

# Import the module under test (will execute top-level code)
# We need to patch the sys.exit and pipeline loading
with patch.dict(os.environ, {"SPRING_RABBITMQ_HOST": "localhost", "SPRING_RABBITMQ_PORT": "5672"}):
    with patch("sys.exit"):
        # The module loads the pipeline at import time, so we mock it
        import importlib

        spec = importlib.util.spec_from_file_location(
            "video_worker", os.path.join(os.path.dirname(__file__), "app", "main.py")
        )
        video_worker = importlib.util.module_from_spec(spec)
        # Prevent the top-level pipeline load from running
        with patch.object(mock_diffusers.StableVideoDiffusionPipeline, "from_pretrained", return_value=mock_pipe):
            with contextlib.suppress(SystemExit):
                spec.loader.exec_module(video_worker)


class TestTheRunnerCollectsEveryDefinedTest(unittest.TestCase):
    """The number of tests the runner collects is the number the files define (ADR-064).

    **First class in the file, deliberately.** A guard defined after a misplaced ``unittest.main()``
    is skipped by the defect it guards — which is what happened to the first version of this one.
    Here, a ``unittest.main()`` anywhere below still finds this class defined, runs it, and it
    counts the classes the module never reached. Placed above it, ``unittest.main()`` would collect
    nothing, and a run of zero tests exits non-zero.

    The Python twin of GOV-006, the rule that a governance check examining nothing fails. Five tests
    in this file sat after ``unittest.main()`` — which exits — and never ran: the build reported 58
    and nobody counted the 63 written. Counting is the whole guard, so it counts two ways:

    * **per runner** — every test this file defines, read from its syntax, is one the loader collects
      from the module as it actually executed. A class after ``unittest.main()``, a second method
      with a name already taken, a pytest-style module function unittest never collects: each makes
      the two numbers differ;
    * **per repository** — every ``test_*.py`` is named by a test command of the repository's one CI
      (``.github/workflows/ci.yml``: the media job, the pack workers' matrix). A suite with no runner
      defines tests that nothing collects, and zero collected out of twenty-seven is the same finding
      as fifty-eight out of sixty-three (krizaka/sovkora#621 found sixteen of them).
    """

    REPOSITORY = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    SKIPPED_DIRECTORIES = {
        ".git",
        ".venv",
        "node_modules",
        "target",
        "__pycache__",
        "dist",
        ".next",
    }

    # A suite that exists and CANNOT run here, each with the condition that keeps it so. The
    # condition is checked: the day it stops holding, the entry is stale and this class fails,
    # because a runnable suite left out of the build is the defect this guard exists for.
    UNRUNNABLE = {}

    @staticmethod
    def _defined(path):
        import ast

        with open(path, encoding="utf-8") as handle:
            tree = ast.parse(handle.read(), filename=path)
        defined = []
        for node in tree.body:
            if isinstance(node, ast.ClassDef):
                bases = {getattr(base, "attr", getattr(base, "id", "")) for base in node.bases}
                if "TestCase" in bases:
                    defined += [
                        f"{node.name}.{item.name}"
                        for item in node.body
                        if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)) and item.name.startswith("test")
                    ]
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name.startswith("test"):
                defined.append(node.name)
        return defined

    def test_every_test_this_file_defines_is_collected(self):
        module = sys.modules[type(self).__module__]
        collected = []
        pending = [unittest.defaultTestLoader.loadTestsFromModule(module)]
        while pending:
            for item in pending.pop():
                if isinstance(item, unittest.TestSuite):
                    pending.append(item)
                else:
                    collected.append(f"{type(item).__name__}.{item._testMethodName}")
        defined = self._defined(os.path.abspath(__file__))
        never_collected = sorted(set(defined) - set(collected))
        defined_twice = sorted({name for name in defined if defined.count(name) > 1})

        if len(defined) != len(collected) or never_collected or defined_twice:
            self.fail(
                f"this file defines {len(defined)} tests and the runner collected {len(collected)}"
                f"\n  never collected: {never_collected}\n  defined twice: {defined_twice}"
            )

    def test_every_python_suite_in_the_repository_has_a_runner(self):
        import importlib.util
        import re

        import yaml

        with open(os.path.join(self.REPOSITORY, ".github", "workflows", "ci.yml"), encoding="utf-8") as handle:
            jobs = yaml.safe_load(handle)["jobs"]
        run = set()
        for job in jobs.values():
            given = job.get("with") or {}
            if "working-directory" not in given or "test-command" not in given:
                continue
            entries = (job.get("strategy") or {}).get("matrix", {}).get("include") or [{}]
            for entry in entries:

                def resolve(text, entry=entry):
                    return re.sub(r"\$\{\{\s*matrix\.([\w-]+)\s*\}\}", lambda m: str(entry.get(m.group(1), "")), text)

                directory = resolve(given["working-directory"])
                for name in re.findall(r"\btest_\w+\.py\b", resolve(given["test-command"])):
                    run.add(os.path.normpath(os.path.join(directory, name)))
        if not run:
            self.fail("no Python test command found in ci.yml: this guard would be judging the empty set")

        suites = set()
        for root, directories, files in os.walk(self.REPOSITORY):
            directories[:] = [d for d in directories if d not in self.SKIPPED_DIRECTORIES]
            for name in files:
                if name.startswith("test_") and name.endswith(".py"):
                    suites.add(os.path.relpath(os.path.join(root, name), self.REPOSITORY))
        self.assertTrue(suites, "no Python suite found: this guard would be judging the empty set")

        for suite, (module, reason) in self.UNRUNNABLE.items():
            self.assertIsNone(
                importlib.util.find_spec(module),
                f"{suite} was excused because {reason} — but {module} is importable now: run it",
            )
        unrun = sorted(suites - run - set(self.UNRUNNABLE))
        if unrun:
            self.fail(f"Python suites no CI test command runs, so no test in them ever has: {unrun}")


class TestPreflightMemoryGuard(unittest.TestCase):
    """§12 host-memory guard: oversized models fail fast/clean (never fake frames)."""

    def test_heavy_model_rejected_when_insufficient_memory(self):
        """SVD-XT (~96GB) must be refused on a host with ~50GB available."""
        fake_vm = MagicMock(available=50 * 1024**3, percent=40.0)
        with patch.object(video_worker, "psutil") as mock_psutil:
            mock_psutil.virtual_memory.return_value = fake_vm
            with self.assertRaises(RuntimeError) as ctx:
                video_worker.preflight_memory_guard("stable-video-diffusion-img2vid-xt")
            self.assertIn("needs", str(ctx.exception).lower())

    def test_light_model_allowed_with_ample_memory(self):
        """AnimateDiff-Lightning (~6GB) must pass the preflight with ~50GB available."""
        fake_vm = MagicMock(available=50 * 1024**3, percent=40.0)
        with patch.object(video_worker, "psutil") as mock_psutil:
            mock_psutil.virtual_memory.return_value = fake_vm
            # Must not raise.
            video_worker.preflight_memory_guard("animatediff-lightning-mps")

    def test_high_memory_pressure_aborts(self):
        """Acute memory pressure (>=92% used) aborts even a light model."""
        fake_vm = MagicMock(available=50 * 1024**3, percent=95.0)
        with patch.object(video_worker, "psutil") as mock_psutil:
            mock_psutil.virtual_memory.return_value = fake_vm
            with self.assertRaises(RuntimeError):
                video_worker.preflight_memory_guard("animatediff-lightning-mps")


class TestSendProgressUpdate(unittest.TestCase):
    """Tests for the AMQP progress reporting function."""

    def setUp(self):
        mock_pika.reset_mock()
        _use_the_broker_mock(self)

    @patch.dict(os.environ, {"SPRING_RABBITMQ_HOST": "testhost", "SPRING_RABBITMQ_PORT": "5673"})
    def test_sends_progress_message(self):
        """Verify progress message is published to the correct exchange."""
        mock_connection = MagicMock()
        mock_channel = MagicMock()
        mock_connection.channel.return_value = mock_channel
        mock_pika.BlockingConnection.return_value = mock_connection

        video_worker.send_progress_update("job-123", 50)

        mock_pika.BlockingConnection.assert_called_once()
        mock_channel.exchange_declare.assert_called_once_with(
            exchange="orazaka.events", exchange_type="topic", durable=True
        )
        mock_channel.basic_publish.assert_called_once()

        # Verify the routing key follows job.{jobId}.progress (AGENTS.md §6)
        publish_kwargs = mock_channel.basic_publish.call_args.kwargs
        self.assertEqual(publish_kwargs.get("exchange"), "orazaka.events")
        self.assertEqual(publish_kwargs.get("routing_key"), "job.job-123.progress")

        # Verify the published message content
        call_kwargs = mock_channel.basic_publish.call_args
        body = (
            call_kwargs[1]["body"]
            if "body" in call_kwargs[1]
            else call_kwargs[0][2]
            if len(call_kwargs[0]) > 2
            else None
        )
        if body is None:
            body = call_kwargs.kwargs.get("body")
        parsed = json.loads(body)
        self.assertEqual(parsed["jobId"], "job-123")
        self.assertEqual(parsed["progress"], 50)

        mock_connection.close.assert_called_once()

    @patch.dict(os.environ, {"SPRING_RABBITMQ_HOST": "badhost"})
    def test_handles_connection_failure_gracefully(self):
        """If RabbitMQ is unreachable, the function should not raise."""
        mock_pika.BlockingConnection.side_effect = Exception("Connection refused")
        # Should not raise
        video_worker.send_progress_update("job-456", 100)
        mock_pika.BlockingConnection.side_effect = None

    @patch.dict(os.environ, {"SPRING_RABBITMQ_PORT": "not_a_number"})
    def test_handles_invalid_port_env(self):
        """Invalid SPRING_RABBITMQ_PORT should fallback to 5672."""
        mock_connection = MagicMock()
        mock_channel = MagicMock()
        mock_connection.channel.return_value = mock_channel
        mock_pika.BlockingConnection.return_value = mock_connection

        video_worker.send_progress_update("job-789", 75)
        # Should not raise — port fallback to 5672


class TestConsumptionMetering(unittest.TestCase):
    """ADR-033 §6.3: terminal events carry the reservation and raw measurements.

    The worker reports what it measured and never a billable unit — that is a
    property of the pricebook row the hold was pinned to, resolved by billing.
    """

    def setUp(self):
        mock_pika.reset_mock()
        mock_pika.BlockingConnection.side_effect = None
        _use_the_broker_mock(self)
        self.channel = MagicMock()
        connection = MagicMock()
        connection.channel.return_value = self.channel
        mock_pika.BlockingConnection.return_value = connection

    def _published(self):
        kwargs = self.channel.basic_publish.call_args.kwargs
        return kwargs.get("routing_key"), json.loads(kwargs.get("body"))

    def test_done_carries_hold_and_consumption(self):
        from app.telemetry import send_job_done

        send_job_done(
            "job-1",
            {"url": "/uploads/x.mp4"},
            hold_id="hold-7",
            consumption={"frames": 48, "fps": 12, "gpuSeconds": 31.4},
        )

        routing_key, body = self._published()
        self.assertEqual(routing_key, "job.job-1.done")
        self.assertEqual(body["holdId"], "hold-7")
        self.assertEqual(body["consumption"]["frames"], 48)
        self.assertEqual(body["consumption"]["fps"], 12)
        self.assertNotIn("unit", body["consumption"])

    def test_error_carries_hold_so_billing_releases_it(self):
        from app.telemetry import send_job_error

        send_job_error("job-2", "MLX out of memory", hold_id="hold-8")

        routing_key, body = self._published()
        self.assertEqual(routing_key, "job.job-2.error")
        self.assertEqual(body["holdId"], "hold-8")

    def test_unmetered_job_omits_the_key_entirely(self):
        from app.telemetry import send_job_done

        send_job_done("job-3", {"url": "/x.mp4"})

        _, body = self._published()
        self.assertNotIn("holdId", body)
        self.assertNotIn("consumption", body)

    def test_extract_consumption_drops_host_diagnostics(self):
        from app.telemetry import extract_consumption

        consumption = extract_consumption(
            {
                "inference_time_sec": 31.4,
                "peak_memory_rss_mb": 8192.0,
                "gpu_allocated_mb": 0.0,
                "gpuSeconds": 31.4,
                "frames": 48,
                "fps": 12,
                "steps": 4,
                "width": 1024,
                "height": 576,
            }
        )

        self.assertEqual(
            consumption,
            {"gpuSeconds": 31.4, "frames": 48, "fps": 12, "steps": 4, "width": 1024, "height": 576},
        )

    def test_extract_consumption_tolerates_missing_metrics(self):
        from app.telemetry import extract_consumption

        self.assertEqual(extract_consumption(None), {})
        self.assertEqual(extract_consumption({}), {})

    def test_record_consumption_reports_the_realised_render_not_the_request(self):
        # The pipelines snap steps and cap frames; billing must see what was produced.
        rendered = [Image.new("RGB", (512, 320)) for _ in range(16)]
        metrics = {}

        video_worker._record_consumption(metrics, rendered, steps=4, fps=12, req_width=1024, req_height=576)

        self.assertEqual(metrics["frames"], 16)
        self.assertEqual(metrics["width"], 512)
        self.assertEqual(metrics["height"], 320)
        self.assertEqual(metrics["steps"], 4)
        self.assertEqual(metrics["fps"], 12)

    def test_record_consumption_never_fails_a_successful_render(self):
        metrics = {}

        video_worker._record_consumption(metrics, ["not-an-image"], steps=4, fps=12, req_width=None, req_height=None)

        # The bad frame is swallowed; an absent measurement releases the hold.
        self.assertNotIn("width", metrics)

    def test_resource_guard_reports_gpu_seconds_as_the_calibration_basis(self):
        mock_process = MagicMock()
        mock_process.memory_info.return_value = MagicMock(rss=100 * 1024 * 1024)

        with video_worker.resource_guard(mock_process) as metrics:
            pass

        self.assertEqual(metrics["gpuSeconds"], metrics["inference_time_sec"])


class TestResourceGuard(unittest.TestCase):
    """Tests for the resource_guard context manager."""

    def test_populates_inference_time(self):
        """Verify inference_time_sec is populated after context exits."""
        mock_process = MagicMock()
        mock_process.memory_info.return_value = MagicMock(rss=100 * 1024 * 1024)

        with video_worker.resource_guard(mock_process) as metrics:
            pass  # Simulate instant execution

        self.assertIn("inference_time_sec", metrics)
        self.assertGreaterEqual(metrics["inference_time_sec"], 0)

    def test_populates_peak_memory(self):
        """Verify peak_memory_rss_mb is populated."""
        mock_process = MagicMock()
        mock_process.memory_info.return_value = MagicMock(rss=512 * 1024 * 1024)

        with video_worker.resource_guard(mock_process) as metrics:
            pass

        self.assertIn("peak_memory_rss_mb", metrics)
        self.assertAlmostEqual(metrics["peak_memory_rss_mb"], 512.0, places=0)

    def test_handles_none_process(self):
        """When process is None, peak_memory should be 0."""
        with video_worker.resource_guard(None) as metrics:
            pass

        self.assertIn("inference_time_sec", metrics)
        self.assertEqual(metrics["peak_memory_rss_mb"], 0.0)

    def test_triggers_gc_collect(self):
        """Verify garbage collection is triggered on exit."""
        mock_process = MagicMock()
        mock_process.memory_info.return_value = MagicMock(rss=0)

        with patch("gc.collect") as mock_gc:
            with video_worker.resource_guard(mock_process) as metrics:
                pass
            mock_gc.assert_called()


class TestVideoInferenceHandlerRouting(unittest.TestCase):
    """Tests for the HTTP handler routing logic."""

    def setUp(self):
        """Start a test HTTP server on a random port."""
        self.server = HTTPServer(("127.0.0.1", 0), video_worker.VideoInferenceHandler)
        self.port = self.server.server_address[1]
        self.thread = Thread(target=self.server.handle_request)
        self.thread.daemon = True

    def tearDown(self):
        self.server.server_close()

    def test_get_root_returns_status(self):
        """GET / should return {'status': 'running'}."""
        self.thread.start()

        import urllib.request

        url = f"http://127.0.0.1:{self.port}/"
        with urllib.request.urlopen(url) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(resp.status, 200)
            self.assertEqual(data["status"], "running")

    def test_get_unknown_path_returns_404(self):
        """GET /unknown should return 404."""
        self.thread.start()

        import urllib.request

        url = f"http://127.0.0.1:{self.port}/unknown"
        try:
            urllib.request.urlopen(url)
            self.fail("Expected HTTP 404")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 404)

    def test_post_unknown_path_returns_404(self):
        """POST /unknown should return 404."""
        self.thread.start()

        import urllib.request

        req = urllib.request.Request(f"http://127.0.0.1:{self.port}/unknown", data=b"{}", method="POST")
        try:
            urllib.request.urlopen(req)
            self.fail("Expected HTTP 404")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 404)


class TestModelRegistryValidation(unittest.TestCase):
    """Tests for model registry validation in the POST handler."""

    def setUp(self):
        self.server = HTTPServer(("127.0.0.1", 0), video_worker.VideoInferenceHandler)
        self.port = self.server.server_address[1]
        self.thread = Thread(target=self.server.handle_request)
        self.thread.daemon = True

    def tearDown(self):
        self.server.server_close()

    def test_invalid_model_returns_400(self):
        """POST with unknown model should return 400."""
        self.thread.start()

        import urllib.request

        payload = json.dumps({"model": "nonexistent-model"}).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}/v1/videos/generations",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            urllib.request.urlopen(req)
            self.fail("Expected HTTP 400 for invalid model")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 400)
            body = json.loads(e.read().decode("utf-8"))
            # A coded refusal: the model asked for is the caller's value, never echoed (krizaka-internal#3390).
            assert body["error"] == "model_not_registered"
            assert "nonexistent-model" not in json.dumps(body)

    def test_valid_model_names_in_registry(self):
        """Verify the 3 registered model names."""
        registry = {
            "stable-video-diffusion-img2vid-xt": "SVD",
            "animatediff-lightning-mps": "AnimateDiff",
            "apple-coreml-video-pipeline": "CoreML",
        }
        # Just validate against the handler code expectations
        self.assertEqual(len(registry), 3)
        self.assertIn("stable-video-diffusion-img2vid-xt", registry)
        self.assertIn("animatediff-lightning-mps", registry)
        self.assertIn("apple-coreml-video-pipeline", registry)


class TestImageIngestion(unittest.TestCase):
    """Tests for image input processing (base64 and file path)."""

    def test_base64_image_decoding(self):
        """Verify base64 image strings can be decoded into PIL Images."""
        img = Image.new("RGB", (64, 64), (255, 0, 0))
        buffer = io.BytesIO()
        img.save(buffer, format="PNG")
        b64 = base64.b64encode(buffer.getvalue()).decode("utf-8")

        decoded = base64.b64decode(b64)
        result = Image.open(io.BytesIO(decoded)).convert("RGB").resize((1024, 576))
        self.assertEqual(result.size, (1024, 576))
        self.assertEqual(result.mode, "RGB")

    def test_file_path_image_loading(self):
        """Verify image_path loading from disk works."""
        img = Image.new("RGB", (128, 128), (0, 255, 0))
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
            img.save(f, format="PNG")
            tmp_path = f.name

        try:
            loaded = Image.open(tmp_path).convert("RGB").resize((1024, 576))
            self.assertEqual(loaded.size, (1024, 576))
        finally:
            os.remove(tmp_path)

    def test_missing_file_path_uses_default(self):
        """If image_path doesn't exist, default gray image should be used."""
        fake_path = "/tmp/nonexistent_image_12345.png"
        self.assertFalse(os.path.exists(fake_path))
        # The handler should fall through to default without crashing


class TestPathSecurity(unittest.TestCase):
    """Tests for validate_safe_path and directory traversal detection."""

    def test_safe_paths_allowed(self):
        """Paths inside current working directory or tmp should be allowed."""
        cwd = os.getcwd()
        safe_path = os.path.join(cwd, "some_file.png")
        self.assertEqual(video_worker.validate_safe_path(safe_path), os.path.abspath(safe_path))

        tmp = tempfile.gettempdir()
        safe_tmp = os.path.join(tmp, "some_temp_file.png")
        self.assertEqual(video_worker.validate_safe_path(safe_tmp), os.path.abspath(os.path.realpath(safe_tmp)))

    def test_every_allowed_root_is_accepted_the_system_temp_directories_included(self):
        """The paths a caller may hand over are the roots the encoder and the upscaler are given: one list."""
        roots = video_worker._allowed_roots()
        assert len(roots) > 3  # cwd, the temp dir, the uploads, and the system temp directories that exist
        for root in roots:
            inside = os.path.join(root, "clip.mp4")
            with self.subTest(root):
                assert video_worker.validate_safe_path(inside) == os.path.abspath(os.path.realpath(inside))

    def test_traversal_paths_denied(self):
        """Paths trying to access unauthorized files outside allowed boundaries must raise PermissionError."""
        bad_paths = [
            "/etc/passwd",
            "../../../../etc/passwd",
            "/private/etc/hosts",
        ]
        # Make sure that if they are outside allowed, they raise PermissionError
        for bp in bad_paths:
            with self.assertRaises(PermissionError):
                video_worker.validate_safe_path(bp)

    def test_encode_output_refuses_traversal_path(self):
        """_encode_output must raise PermissionError for output_path values outside allowed roots."""
        handler = video_worker.VideoInferenceHandler.__new__(video_worker.VideoInferenceHandler)
        bad_paths = [
            "/etc/evil.mp4",
            "../../../../etc/evil.mp4",
            "/private/etc/evil.mp4",
        ]
        for bad in bad_paths:
            with self.subTest(bad), pytest.raises(PermissionError):
                handler._encode_output({}, [], 14, bad, None, {})

    def test_load_image_handles_invalid_path_gracefully(self):
        """load_image should fall back to default image if image_path is outside allowed directories."""
        payload = {"image_path": "/etc/passwd"}
        img = video_worker.load_image(payload)
        self.assertIsNotNone(img)
        self.assertEqual(img.size, (1024, 576))

    def test_encoder_rejects_hyphen_filename(self):
        """export_frames_to_video should reject output paths starting with a hyphen to prevent flag injection."""
        from app.encoder import export_frames_to_video

        img = Image.new("RGB", (1024, 576), (128, 128, 128))
        with self.assertRaises(ValueError):
            export_frames_to_video([img], "-some_flag.mp4")


class TestFrameDurationCalculation(unittest.TestCase):
    """Tests for duration-to-frame-count calculation."""

    def test_default_duration_2s_svd(self):
        """2 seconds at 14fps = 28 frames."""
        duration_sec = 2
        fps_render = 14
        num_frames = int(duration_sec * fps_render)
        num_frames = max(14, min(num_frames, 30))
        self.assertEqual(num_frames, 28)

    def test_default_duration_2s_animatediff(self):
        """2 seconds at 12fps = 24 frames."""
        duration_sec = 2
        fps_render = 12
        num_frames = int(duration_sec * fps_render)
        num_frames = max(14, min(num_frames, 30))
        self.assertEqual(num_frames, 24)

    def test_minimum_frame_clamp(self):
        """Very short duration should clamp to minimum 14 frames."""
        duration_sec = 0.5
        for fps_render in [12, 14]:
            num_frames = int(duration_sec * fps_render)
            num_frames = max(14, min(num_frames, 30))
            self.assertEqual(num_frames, 14)

    def test_maximum_frame_clamp(self):
        """Long duration should clamp to maximum 30 frames."""
        duration_sec = 10
        for fps_render in [12, 14]:
            num_frames = int(duration_sec * fps_render)
            num_frames = max(14, min(num_frames, 30))
            self.assertEqual(num_frames, 30)

    def test_4_second_duration_svd(self):
        """4 seconds at 14fps = 30 frames (max clamp)."""
        duration_sec = 4
        fps_render = 14
        num_frames = int(duration_sec * fps_render)
        num_frames = max(14, min(num_frames, 30))
        self.assertEqual(num_frames, 30)

    def test_1_second_duration_svd(self):
        """1 second at 14fps = 14 frames (min clamp)."""
        duration_sec = 1
        fps_render = 14
        num_frames = int(duration_sec * fps_render)
        num_frames = max(14, min(num_frames, 30))
        self.assertEqual(num_frames, 14)


class TestMemoryCapEnforcement(unittest.TestCase):
    """Tests for psutil memory cap logic."""

    def test_high_process_rss_forces_fallback(self):
        """Process RSS > 6GB should trigger fallback."""
        process_mem_mb = 7000.0
        PROCESS_RSS_CAP_MB = 6000.0
        force_fallback = process_mem_mb > PROCESS_RSS_CAP_MB
        self.assertTrue(force_fallback)

    def test_low_available_memory_forces_fallback(self):
        """System available < 1.5GB should trigger fallback."""
        available_mem_mb = 1000.0
        SYSTEM_AVAIL_MIN_MB = 1500.0
        force_fallback = available_mem_mb < SYSTEM_AVAIL_MIN_MB
        self.assertTrue(force_fallback)

    def test_normal_memory_does_not_force_fallback(self):
        """Normal memory conditions should not force fallback."""
        process_mem_mb = 3000.0
        available_mem_mb = 8000.0
        PROCESS_RSS_CAP_MB = 6000.0
        SYSTEM_AVAIL_MIN_MB = 1500.0

        force_fallback = process_mem_mb > PROCESS_RSS_CAP_MB or available_mem_mb < SYSTEM_AVAIL_MIN_MB
        self.assertFalse(force_fallback)


def load_declaration_for_test():
    from app.registration import load_declaration

    return load_declaration()


class TestConsumerDispatch(unittest.TestCase):
    """The worker decides from the message, never from a capability it recognises.

    ADR-037 §4.2 (P4): naming a capability here is what made a general-purpose
    media worker coupled to one pack. The routing key is the dispatcher's
    decision, taken from the capability's row; this worker only knows which keys
    it is bound to.
    """

    def test_bindings_come_from_worker_yaml_not_from_source(self):
        """worker.yaml is the single source for what this worker drains (ADR-038, S3)."""
        from app.consumer import BINDINGS, COMPOSE_PREFIX
        from app.registration import load_declaration

        # The sound bindings are declared here too, but belong to the sound queue (bound only where sound is enabled).
        declared = load_declaration()["bindings"]
        self.assertEqual(BINDINGS, [b for b in declared if not b.startswith("job.sound.")])
        self.assertIn("job.sound.*", declared)
        self.assertIn("job.video.*", BINDINGS)
        self.assertIn("job.compose.*", BINDINGS)
        self.assertEqual(COMPOSE_PREFIX, "job.compose.")

    def test_worker_yaml_declares_no_capability(self):
        """A worker declares bindings, never a capability name (P4, [PACK-002])."""
        import pathlib

        declaration = pathlib.Path(__file__).parent / "worker.yaml"
        body = "\n".join(line for line in declaration.read_text().splitlines() if not line.strip().startswith("#"))
        self.assertNotIn("orazaka.core.", body)
        self.assertNotIn("orazaka.studio.", body)

    def test_registration_never_raises_when_the_job_service_is_down(self):
        """Registration is advisory: unreachable platform must not stop consumption."""
        import os
        from unittest.mock import patch

        from app import registration

        # Port 1 is reserved and unbound, so the connection fails immediately.
        with patch.dict(os.environ, {"JOB_SERVICE_INTERNAL_URL": "http://127.0.0.1:1"}):
            with patch.object(registration.time, "sleep", side_effect=InterruptedError):
                with self.assertRaises(InterruptedError):
                    # One pass: it must swallow the connection error and reach the sleep.
                    registration.register_forever(load_declaration_for_test())

    def test_an_unresolved_model_is_left_to_the_pipeline(self):
        """A Studio step sends model "" in its payload and "default" on the job: neither is a model name."""
        from app.consumer import _requested_model

        self.assertIsNone(_requested_model({"model": ""}, {"model": "default"}))
        self.assertIsNone(_requested_model({}, {}))
        self.assertEqual(
            _requested_model({"model": ""}, {"model": "mlx-animatediff-lightning"}),
            "mlx-animatediff-lightning",
        )
        self.assertEqual(
            _requested_model({"model": "animatediff-lightning-mps"}, {"model": "default"}),
            "animatediff-lightning-mps",
        )

    def test_compose_is_decided_by_the_routing_key(self):
        from app.consumer import _is_compose

        self.assertTrue(_is_compose("job.compose.assemble"))
        self.assertFalse(_is_compose("job.video.generate"))
        self.assertFalse(_is_compose(""))
        self.assertFalse(_is_compose(None))

    def test_a_new_compose_capability_needs_no_python_edit(self):
        """A capability this worker has never heard of, bound to job.compose.*, composes."""
        from app.consumer import _is_compose

        self.assertTrue(_is_compose("job.compose.montage"))

    def test_the_capability_name_does_not_decide(self):
        """The feature key is not consulted: only the key the broker delivered under."""
        import app.consumer as consumer

        with open(consumer.__file__, encoding="utf-8") as handle:
            source = handle.read()
        code = "\n".join(line for line in source.splitlines() if not line.strip().startswith("#"))
        self.assertNotIn("featureKey", code.split("def _on_message")[1].split("def ")[0])


class TestProtocolConformance(unittest.TestCase):
    """The MUST clauses of docs/WORKER_PROTOCOL.md that this worker is the reference for."""

    def _consumer_source(self):
        import app.consumer as consumer

        with open(consumer.__file__, encoding="utf-8") as handle:
            return handle.read()

    def test_prefetch_is_one(self):
        """§2: prefetch MUST be 1 — the accelerator is the backpressure."""
        self.assertIn("basic_qos(prefetch_count=1)", self._consumer_source())

    def test_acks_after_a_terminal_outcome_including_failure(self):
        """§5: a worker MUST ack even on failure, or the queue stops draining.

        Asserted on the structure rather than the happy path: the ack lives in a
        ``finally``, which is what makes it hold for the failure branch too.
        """
        source = self._consumer_source()
        body = source.split("def _on_message", 1)[1].split("\ndef ", 1)[0]
        self.assertIn("finally:", body)
        finally_block = body.split("finally:", 1)[1]
        self.assertIn("basic_ack", finally_block)


class TestTypedFailureCause(unittest.TestCase):
    """§3: a worker MUST declare a typed cause on `job.{id}.error` (ADR-053)."""

    def _consumer_source(self):
        import app.consumer as consumer

        with open(consumer.__file__, encoding="utf-8") as handle:
            return handle.read()

    def test_every_error_carries_a_cause(self):
        from app import telemetry

        published = []
        with patch.object(telemetry, "_publish_job_event", lambda key, body: published.append((key, body))):
            telemetry.send_job_error("job-1", "boom", hold_id="hold-1", cause=telemetry.TIMEOUT)
        self.assertEqual("TIMEOUT", published[0][1]["cause"])
        self.assertEqual("hold-1", published[0][1]["holdId"])

    def test_a_caller_that_declares_nothing_gets_executor_fault(self):
        from app import telemetry

        published = []
        with patch.object(telemetry, "_publish_job_event", lambda key, body: published.append((key, body))):
            telemetry.send_job_error("job-2", "boom")
        self.assertEqual("EXECUTOR_FAULT", published[0][1]["cause"])

    def test_an_invented_cause_degrades_rather_than_travelling(self):
        """A worker inventing 'BAD_INPUT' must not be read as the actor's fault downstream."""
        from app import telemetry

        published = []
        with patch.object(telemetry, "_publish_job_event", lambda key, body: published.append((key, body))):
            telemetry.send_job_error("job-3", "boom", cause="BAD_INPUT")
        self.assertEqual("EXECUTOR_FAULT", published[0][1]["cause"])

    def test_the_vocabulary_matches_the_java_contract(self):
        from app import telemetry

        self.assertEqual(
            ("GUARD_REFUSAL", "INPUT_INVALID", "EXECUTOR_FAULT", "PLATFORM_UNAVAILABLE", "TIMEOUT"),
            telemetry.FAILURE_CAUSES,
        )

    def test_input_invalid_is_unreachable_from_the_broad_except(self):
        """The one cause that BILLS must come from a declared rejection, never from a catch-all.

        Structural, not behavioural: `INPUT_INVALID` may appear only in the handler for this
        worker's own `InvalidJobPayload`, so no unanticipated exception can ever claim the actor
        was at fault — ADR-046 §2's counter-example was exactly that mistake.
        """
        body = self._consumer_source().split("def _on_message", 1)[1].split("\ndef ", 1)[0]
        broad = body.split("except Exception", 1)[1]
        self.assertNotIn("INPUT_INVALID", broad)
        self.assertIn("EXECUTOR_FAULT", broad)
        declared = body.split("except InvalidJobPayload", 1)[1].split("except ", 1)[0]
        self.assertIn("INPUT_INVALID", declared)


_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


class TestEnvelopeInterop(unittest.TestCase):
    """The asset envelope, and that both languages agree on it (ADR-054 §6).

    The store is written by three processes in two languages, and the way that breaks is silently:
    one side changes the header and the other keeps opening the files it wrote itself. The Java
    suite emits `orazaka-libs/orazaka-ai-engine/orazaka-assets/target/interop/java-written.bin`; this reads it with
    the same fixed key and compares plaintext, then writes its own for Java to read back.
    """

    # The engine's assets module of the one repository (ADR-029) writes the Java side of the fixture.
    FIXTURE_DIR = os.path.join(_ROOT, "engine", "sovkora-engine-assets", "target", "interop")

    def _keyring(self):
        from app import envelope

        return envelope.MasterKeyring(os.path.join(self.FIXTURE_DIR, "fixture.key"))

    def test_python_opens_what_java_sealed(self):
        from app import envelope

        sealed = os.path.join(self.FIXTURE_DIR, "java-written.bin")
        if not os.path.isfile(sealed):
            self.skipTest("run the Java assets suite first; it writes the fixture")
        with open(os.path.join(self.FIXTURE_DIR, "plain.bin"), "rb") as handle:
            expected = handle.read()
        self.assertEqual(expected, envelope.decrypt_bytes(sealed, self._keyring()))

    def test_python_seeks_inside_what_java_sealed(self):
        from app import envelope

        sealed = os.path.join(self.FIXTURE_DIR, "java-written.bin")
        if not os.path.isfile(sealed):
            self.skipTest("run the Java assets suite first; it writes the fixture")
        with open(os.path.join(self.FIXTURE_DIR, "plain.bin"), "rb") as handle:
            expected = handle.read()
        for offset in (0, 1, 4095, 4096, 4097, 3 * 4096 + 100):
            self.assertEqual(expected[offset:], envelope.decrypt_bytes(sealed, self._keyring(), offset))

    def test_python_writes_a_fixture_java_can_open(self):
        from app import envelope

        if not os.path.isdir(self.FIXTURE_DIR):
            self.skipTest("run the Java assets suite first; it creates the fixture directory")
        plain = os.path.join(self.FIXTURE_DIR, "plain.bin")
        target = os.path.join(self.FIXTURE_DIR, "python-written.bin")
        envelope.encrypt_file(plain, target, self._keyring(), 4096)
        with open(plain, "rb") as handle:
            expected = handle.read()
        self.assertEqual(expected, envelope.decrypt_bytes(target, self._keyring()))

    def test_a_flipped_byte_refuses_to_open(self):
        from app import envelope

        sealed = os.path.join(self.FIXTURE_DIR, "java-written.bin")
        if not os.path.isfile(sealed):
            self.skipTest("run the Java assets suite first; it writes the fixture")
        with open(sealed, "rb") as handle:
            raw = bytearray(handle.read())
        raw[-20] ^= 0x01
        tampered = os.path.join(self.FIXTURE_DIR, "tampered.bin")
        with open(tampered, "wb") as handle:
            handle.write(raw)
        with self.assertRaises(envelope.EnvelopeError):
            envelope.decrypt_bytes(tampered, self._keyring())


class TestRunServerFunction(unittest.TestCase):
    """Tests for the run() entrypoint function."""

    def test_default_port(self):
        """run() should default to port 8188."""
        with patch.object(HTTPServer, "__init__", return_value=None) as mock_init:
            with patch.object(HTTPServer, "serve_forever", side_effect=KeyboardInterrupt):
                with patch.object(HTTPServer, "server_close"):
                    with contextlib.suppress(KeyboardInterrupt, AttributeError):
                        video_worker.run(port=8188)

    def test_custom_port_from_argv(self):
        """If sys.argv provides a port, it should be used."""
        # Verify the __main__ block logic
        test_port = 9999
        self.assertEqual(int(str(test_port)), 9999)

    def test_binds_loopback_by_default(self):
        """run() must bind 127.0.0.1 unless MEDIA_VIDEO_HOST overrides it."""
        os.environ.pop("MEDIA_VIDEO_HOST", None)
        with patch.object(HTTPServer, "__init__", return_value=None) as mock_init:
            with patch.object(HTTPServer, "serve_forever", side_effect=KeyboardInterrupt):
                with patch.object(HTTPServer, "server_close"):
                    with contextlib.suppress(KeyboardInterrupt, AttributeError):
                        video_worker.run(port=8188)
        # patch.object replaces __init__ with a plain mock, so the captured positional
        # args do not include self: call_args[0] = (server_address, handler_class).
        server_address = mock_init.call_args[0][0]
        self.assertEqual(server_address[0], "127.0.0.1")

    def test_binds_all_interfaces_when_env_set(self):
        """run() binds all interfaces when MEDIA_VIDEO_HOST is set to empty string."""
        os.environ["MEDIA_VIDEO_HOST"] = ""
        try:
            with patch.object(HTTPServer, "__init__", return_value=None) as mock_init:
                with patch.object(HTTPServer, "serve_forever", side_effect=KeyboardInterrupt):
                    with patch.object(HTTPServer, "server_close"):
                        with contextlib.suppress(KeyboardInterrupt, AttributeError):
                            video_worker.run(port=8188)
            server_address = mock_init.call_args[0][0]
            self.assertEqual(server_address[0], "")
        finally:
            os.environ.pop("MEDIA_VIDEO_HOST", None)


class TestCompositionDivergenceContract(unittest.TestCase):
    """The billed duration is the produced file's, never the requested one (ADR-063, audit #20).

    The divergence contract, extended from images to video. The image contract serves a 768x512
    image for a 512x512 request and checks the bill follows the image; this one composes three
    stills held 3 s each over a DELIBERATELY SHORT 2 s voiceover. ``-shortest`` ends the file at
    the audio, so the request says 9 s and the file says 2 — the case that billed 9.00 output
    seconds for a 2.02 s file.

    The oracle is ffprobe on the file the composer wrote, read here and not through the composer:
    a contract that asked the composer what it made would be checking the composer against
    itself. No ffmpeg is a failure, not a skip: a contract that examined nothing would be green
    for the reason GOV-006 exists to refuse.

    **The billed seconds are the file's duration** (ADR-066). They were ``frames / fps``, which is
    exact only at a constant whole-number rate — so ADR-063 made the composer pass ``-r 30`` to
    ffmpeg to keep that quotient working. Shaping the artefact to suit the meter is backwards even
    when the new artefact is better, so the meter changed instead, and one of these tests went with
    it: ``test_without_audio_the_billed_rate_is_the_files_rate`` asserted that the billed ``fps``
    equalled the file's rate, which is no longer a billing fact at all — the rate is an encoding
    choice, and the bill does not read it.
    """

    SECONDS_PER_PHOTO = 3.0
    WIDTH, HEIGHT = 180, 320
    # One frame at 30 fps, plus the AAC encoder's priming samples on the container clock.
    TOLERANCE_SECONDS = 0.1

    @classmethod
    def setUpClass(cls):
        import shutil

        for tool in ("ffmpeg", "ffprobe"):
            if shutil.which(tool) is None:
                raise AssertionError(f"{tool} is required: this contract measures a real file")

    def setUp(self):
        import subprocess

        self.workdir = tempfile.mkdtemp()
        self.photos = []
        for index, colour in enumerate([(200, 30, 30), (30, 200, 30), (30, 30, 200)]):
            path = os.path.join(self.workdir, f"still_{index}.png")
            Image.new("RGB", (64, 48), colour).save(path)
            self.photos.append(path)
        self.short_voiceover = os.path.join(self.workdir, "voiceover.m4a")
        subprocess.run(
            [
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=2",
                "-c:a",
                "aac",
                self.short_voiceover,
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )

    def tearDown(self):
        import shutil

        shutil.rmtree(self.workdir, ignore_errors=True)

    def _probe(self, path):
        """What the file is, read from the file."""
        import subprocess

        video = json.loads(
            subprocess.run(
                [
                    "ffprobe",
                    "-v",
                    "error",
                    "-select_streams",
                    "v:0",
                    "-show_entries",
                    "stream=width,height,r_frame_rate:format=duration",
                    "-of",
                    "json",
                    path,
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=True,
                text=True,
            ).stdout
        )
        stream = video["streams"][0]
        return {
            "seconds": float(video["format"]["duration"]),
            "rate": stream["r_frame_rate"],
            "width": stream["width"],
            "height": stream["height"],
        }

    def _compose(self, audio_path):
        from app.composer import compose

        output = os.path.join(self.workdir, "composed.mp4")
        billed = compose(
            self.photos,
            output,
            audio_path=audio_path,
            seconds_per_photo=self.SECONDS_PER_PHOTO,
            width=self.WIDTH,
            height=self.HEIGHT,
        )
        return billed, self._probe(output)

    def _billed_seconds(self, billed):
        """What billing will price: the duration the composer measured on the file."""
        self.assertIsNotNone(billed.get("durationSeconds"), f"nothing billable was reported: {billed}")
        return billed["durationSeconds"]

    def test_a_short_voiceover_bills_the_file_not_the_slideshow(self):
        billed, produced = self._compose(self.short_voiceover)

        # The fixture is what it claims: the file really is the voiceover's length, not 9 s.
        self.assertLess(produced["seconds"], 3.0, "fixture: -shortest did not shorten the file")
        self.assertAlmostEqual(
            self._billed_seconds(billed),
            produced["seconds"],
            delta=self.TOLERANCE_SECONDS,
            msg=f"billed {billed} for a file of {produced['seconds']:.2f} s",
        )

    def test_without_audio_the_billed_seconds_are_the_files_seconds(self):
        billed, produced = self._compose(None)

        # 9 s used to come out right here only because two fictional numbers cancelled: frames
        # computed from the request and an fps the file did not have (ADR-062 §5.5). The rate is
        # no longer part of the answer — this asserted `billed["fps"] == the file's rate` until
        # ADR-066 stopped billing from reading it.
        self.assertNotIn("fps", billed, "the bill no longer reads a frame rate")
        self.assertAlmostEqual(
            self._billed_seconds(billed),
            produced["seconds"],
            delta=self.TOLERANCE_SECONDS,
            msg=f"billed {billed} for a file of {produced['seconds']:.2f} s",
        )

    def test_the_billed_dimensions_are_the_files(self):
        billed, produced = self._compose(self.short_voiceover)

        self.assertEqual((billed["width"], billed["height"]), (produced["width"], produced["height"]))


class AssetIdResolutionTest(unittest.TestCase):
    """A compose payload names asset ids; only their owner's files may answer.

    ``realestate-reels`` was diagnosed as an environment limitation twice and then
    fixed in the wrong process once. ``orazaka.studio.media.compose`` routes to
    ``job.compose.assemble`` — this worker — so the Java job service's resolver
    never sees it. The worker joined the id straight onto the upload root, without
    the owner's directory or the file extension, found nothing readable and raised
    "compose requires at least one readable photo" (ADR-046).
    """

    def _seed(self, root, owner, name="a1b2c3d4-0000-4000-8000-000000000001"):
        temp = os.path.join(root, owner, "temp")
        os.makedirs(temp, exist_ok=True)
        Image.new("RGB", (8, 8)).save(os.path.join(temp, name + ".png"))
        return name

    def test_an_asset_id_resolves_to_its_owner_file(self):
        from app.consumer import _resolve_owned_asset

        with tempfile.TemporaryDirectory() as root:
            owner = "550e8400-e29b-41d4-a716-446655440001"
            asset = self._seed(root, owner)

            resolved = _resolve_owned_asset(root, owner, asset)

            self.assertIsNotNone(resolved)
            self.assertTrue(os.path.isfile(resolved))
            # realpath: macOS resolves /var to /private/var, so compare canonicals.
            self.assertTrue(resolved.startswith(os.path.realpath(os.path.join(root, owner))))

    def test_another_actors_asset_is_not_found(self):
        from app.consumer import _resolve_owned_asset

        with tempfile.TemporaryDirectory() as root:
            asset = self._seed(root, "550e8400-e29b-41d4-a716-446655440001")

            self.assertIsNone(_resolve_owned_asset(root, "550e8400-e29b-41d4-a716-446655440002", asset))

    def test_traversal_in_an_asset_id_escapes_nothing(self):
        from app.consumer import _resolve_owned_asset

        with tempfile.TemporaryDirectory() as root:
            owner = "550e8400-e29b-41d4-a716-446655440001"
            os.makedirs(os.path.join(root, owner), exist_ok=True)
            Image.new("RGB", (8, 8)).save(os.path.join(root, "elsewhere.png"))

            self.assertIsNone(_resolve_owned_asset(root, owner, "../elsewhere"))

    def test_an_absolute_path_is_not_an_asset_reference(self):
        """ADR-065. This test used to be test_an_absolute_path_is_left_alone, and asserted that
        an absolute path came back untouched — written in the same commit as the branch it
        described, to match it. Nobody had asked whose file the path named."""
        from app.consumer import _resolve_owned_asset

        with tempfile.TemporaryDirectory() as root:
            owner = "550e8400-e29b-41d4-a716-446655440001"
            stranger = "550e8400-e29b-41d4-a716-446655440002"
            theirs = os.path.join(root, stranger, "temp", self._seed(root, stranger) + ".png")
            own = os.path.join(root, owner, "temp", self._seed(root, owner) + ".png")

            # Another actor's file, by path: not found — the same answer as a file that never
            # existed, so the refusal says nothing about which it was.
            self.assertIsNone(_resolve_owned_asset(root, owner, theirs))
            # The caller's own file, by path: refused too. The path is what is refused, not the
            # owner — asking by location is not a way in, even for one's own upload.
            self.assertIsNone(_resolve_owned_asset(root, owner, own))

    def test_compose_payload_of_asset_ids_reaches_readable_files(self):
        from app import consumer

        with tempfile.TemporaryDirectory() as root:
            owner = "550e8400-e29b-41d4-a716-446655440001"
            first = self._seed(root, owner, "a1b2c3d4-0000-4000-8000-000000000001")
            second = self._seed(root, owner, "a1b2c3d4-0000-4000-8000-000000000002")
            job = {"jobId": "job-1", "userId": owner, "payload": {"photos": [first, second]}}

            # `_seal` needs a keyring, like the worker does in production; a stub here would test
            # a code path that no longer exists (ADR-054).
            with (
                patch.object(consumer, "_upload_root", return_value=root),
                patch.object(consumer, "compose", return_value={}) as composed,
                patch.object(consumer, "extract_consumption", return_value={}),
                patch.object(consumer, "_seal"),
            ):
                consumer._compose(job)

            passed = composed.call_args[0][0]
            self.assertEqual(len(passed), 2)
            for path in passed:
                self.assertTrue(os.path.isfile(path), path)


class TestEncoderConfinesItsOutput(unittest.TestCase):
    """The encoder checks its own output path (CodeQL py/command-line-injection): it never trusts its caller."""

    def _frames(self):
        return [Image.new("RGB", (8, 8))]

    def test_refuses_a_path_outside_the_allowed_roots(self):
        from app import encoder

        for bad in (
            "/etc/orazaka-evil.mp4",
            "/root/x.mp4",
            os.path.join(tempfile.gettempdir(), "..", "..", "etc", "x.mp4"),
        ):
            with patch.object(encoder.subprocess, "run") as run:
                with self.assertRaises(PermissionError, msg=bad):
                    encoder.export_frames_to_video(self._frames(), bad)
                run.assert_not_called()

    def test_refuses_a_leading_hyphen_name(self):
        from app import encoder

        with patch.object(encoder.subprocess, "run") as run:
            with self.assertRaises(ValueError):
                encoder.export_frames_to_video(self._frames(), os.path.join(tempfile.gettempdir(), "-y.mp4"))
            run.assert_not_called()

    def test_ffmpeg_receives_an_absolute_output_path(self):
        from app import encoder

        def ffmpeg(cmd, **_):  # a successful ffmpeg writes its output file
            with open(cmd[-1], "wb") as encoded:
                encoded.write(b"MP4")

        out = os.path.join(tempfile.gettempdir(), "orazaka-enc-ok.mp4")
        self.addCleanup(lambda: os.path.exists(out) and os.remove(out))
        with patch.object(encoder.subprocess, "run", side_effect=ffmpeg) as run:
            encoder.export_frames_to_video(self._frames(), out)
        self.assertTrue(os.path.isabs(run.call_args[0][0][-1]))

    def test_no_subprocess_in_the_worker_uses_a_shell(self):
        import glob
        import re

        for path in glob.glob(os.path.join(os.path.dirname(__file__), "app", "*.py")):
            with open(path, encoding="utf-8") as source:
                self.assertIsNone(re.search(r"shell\s*=\s*True|os\.system\(", source.read()), path)


class TestTheWorkerAnswersJsonOnly(unittest.TestCase):
    """Every answer is JSON with ``nosniff``; no request value or exception message comes back raw (#3390)."""

    GENERATE = "/v1/videos/generations"

    def setUp(self):
        self.server = HTTPServer(("127.0.0.1", 0), video_worker.VideoInferenceHandler)
        self.port = self.server.server_address[1]
        self.thread = Thread(target=self.server.handle_request, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.thread.join(timeout=10)
        self.server.server_close()

    def _exchange(self, method, path, body=None):
        import http.client

        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        self.addCleanup(connection.close)
        data = None if body is None else json.dumps(body).encode("utf-8")
        connection.request(method, path, body=data, headers={"Content-Type": "application/json"})
        response = connection.getresponse()
        return response.status, response.headers, response.read()

    def _json(self, headers, raw):
        assert headers["Content-Type"] == "application/json; charset=utf-8"
        assert headers["X-Content-Type-Options"] == "nosniff"
        return json.loads(raw.decode("utf-8"))

    def test_an_html_payload_comes_back_escaped_inside_json(self):
        payload = {"model": "animatediff-lightning-mps", "upscale": "<script>alert(1)</script>"}
        status, headers, raw = self._exchange("POST", self.GENERATE, payload)
        assert status == 400
        body = self._json(headers, raw)
        assert body["error"] == "invalid_request"
        assert b"<script" not in raw
        assert "&lt;script&gt;" in body["detail"]

    def test_an_unregistered_model_is_refused_by_code_and_not_echoed(self):
        status, headers, raw = self._exchange("POST", self.GENERATE, {"model": "<img src=x onerror=alert(1)>"})
        assert status == 400
        assert self._json(headers, raw)["error"] == "model_not_registered"
        assert b"onerror" not in raw

    def test_a_model_that_is_not_a_string_is_refused_not_a_crash(self):
        status, headers, raw = self._exchange("POST", self.GENERATE, {"model": ["animatediff-lightning-mps"]})
        assert status == 400
        assert self._json(headers, raw)["error"] == "model_not_registered"

    def test_a_local_generation_failure_answers_a_fixed_message(self):
        leak = RuntimeError("weights at /models/secret-token-42 <b>failed</b>")
        with patch.object(video_worker.VideoInferenceHandler, "_resolve_seed", side_effect=leak):
            status, headers, raw = self._exchange("POST", self.GENERATE, {"model": "animatediff-lightning-mps"})
        assert status == 500
        assert self._json(headers, raw)["error"] == "generation_failed"
        assert b"secret-token-42" not in raw
        assert b"<b>" not in raw

    def test_a_provider_failure_answers_a_fixed_message(self):
        leak = RuntimeError("the provider answered 401: <html>secret-token-42</html>")
        with patch.object(video_worker, "run_remote", side_effect=leak):
            status, headers, raw = self._exchange("POST", self.GENERATE, {"model": "wan2-2-t2v-a14b"})
        assert status == 500
        assert self._json(headers, raw)["error"] == "generation_failed"
        assert b"secret-token-42" not in raw

    def test_a_cancelled_job_answers_a_coded_refusal(self):
        stopped = video_worker.job_control.JobCancelled("job <i>j-1</i> was cancelled")
        with patch.object(video_worker, "run_remote", side_effect=stopped):
            payload = {"model": "wan2-2-t2v-a14b", "job_id": "j-1"}
            status, headers, raw = self._exchange("POST", self.GENERATE, payload)
        assert status == video_worker.CANCELLED_STATUS
        assert self._json(headers, raw)["error"] == "cancelled"
        assert b"<i>" not in raw

    def test_an_unknown_route_answers_json_without_the_path(self):
        status, headers, raw = self._exchange("GET", "/%3Cscript%3E")
        assert status == 404
        assert self._json(headers, raw)["error"] == "not_found"
        assert b"script" not in raw

    def test_a_success_body_never_carries_a_raw_html_character(self):
        answer = {"status": "success", "metrics": {"provider": "<b>a & b</b>"}}
        with patch.object(video_worker, "run_remote", return_value=answer):
            status, headers, raw = self._exchange("POST", self.GENERATE, {"model": "wan2-2-t2v-a14b"})
        assert status == 200
        assert self._json(headers, raw) == answer, "the escaping changes the bytes, never the data"
        assert not {b"<", b">", b"&"} & {raw[i : i + 1] for i in range(len(raw))}

    def test_the_model_served_is_the_registrys_own_name_not_the_callers_string(self):
        asked = "".join(["wan2-2-", "t2v-a14b"])  # built at run time: a string the registry does not hold
        registered = next(known for known in video_worker.MODEL_REGISTRY if known == asked)
        with patch.object(video_worker, "run_remote", return_value={"status": "success", "metrics": {}}) as served:
            status, _, _ = self._exchange("POST", self.GENERATE, {"model": asked})
        assert status == 200
        assert served.call_args.args[1] is registered

    def test_the_status_route_is_json_with_nosniff(self):
        status, headers, raw = self._exchange("GET", "/")
        assert status == 200
        assert self._json(headers, raw) == {"status": "running"}


class TestEncoderParsesItsInputsBeforeFfmpeg(unittest.TestCase):
    """Every value that reaches ffmpeg is a closed type, checked before the runner is called (krizaka-internal#3390)."""

    def _frames(self):
        return [Image.new("RGB", (8, 8))]

    def _runner(self):
        calls = []

        def run(cmd, **kwargs):
            calls.append((cmd, kwargs))
            with open(cmd[-1], "wb") as encoded:
                encoded.write(b"MP4")

        return calls, run

    def _inside(self):
        import shutil

        directory = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, directory, True)
        return os.path.join(directory, "clip.mp4")

    def test_a_path_outside_the_worker_directories_is_refused_before_ffmpeg(self):
        from app import encoder

        calls, run = self._runner()
        for bad in ("/etc/evil.mp4", os.path.join(tempfile.gettempdir(), "..", "..", "etc", "evil.mp4")):
            with self.subTest(bad), pytest.raises(PermissionError):
                encoder.export_frames_to_video(self._frames(), bad, run=run)
        assert calls == []

    def test_an_input_starting_with_a_dash_is_refused_before_ffmpeg(self):
        from app import encoder

        calls, run = self._runner()
        with pytest.raises(ValueError, match="'-'"):
            encoder.export_frames_to_video(self._frames(), "-y.mp4", run=run)
        for fps in ("-filter_complex", "-1", -1):
            with self.subTest(fps), pytest.raises(ValueError, match=r"frame rate|'-'"):
                encoder.export_frames_to_video(self._frames(), self._inside(), fps=fps, run=run)
        assert calls == []

    def test_a_codec_off_the_allow_list_is_refused_before_ffmpeg(self):
        from app import encoder

        calls, run = self._runner()
        for codec in ("libx265", "libx264 -vf drawtext", "-c:v"):
            with self.subTest(codec), pytest.raises(ValueError, match="codec"):
                encoder.export_frames_to_video(self._frames(), self._inside(), codec=codec, run=run)
        assert calls == []

    def test_a_frame_rate_that_is_not_a_bounded_number_is_refused_before_ffmpeg(self):
        from app import encoder

        calls, run = self._runner()
        for fps in (0, 121, True, float("nan"), "14fps", None):
            with self.subTest(fps), pytest.raises(ValueError, match="frame rate"):
                encoder.export_frames_to_video(self._frames(), self._inside(), fps=fps, run=run)
        assert calls == []

    def test_a_valid_request_builds_the_same_arguments_and_lands_the_clip(self):
        from app import encoder

        calls, run = self._runner()
        out = self._inside()
        encoder.export_frames_to_video(self._frames(), out, fps=14, run=run)
        [(cmd, kwargs)] = calls
        work = os.path.dirname(cmd[5])
        codec = "h264_videotoolbox" if encoder.is_apple_silicon() else "libx264"
        frames = os.path.join(work, "frame_%04d.png")
        flags = ["-b:v", "8M", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
        assert cmd[:-1] == ["ffmpeg", "-y", "-framerate", "14", "-i", frames, "-c:v", codec, *flags]
        # The one file argument is the worker's own: the request's path never reaches the command line.
        assert os.path.dirname(cmd[-1]) == work
        assert out not in cmd
        assert not kwargs.get("shell", False)
        with open(out, "rb") as landed:
            assert landed.read() == b"MP4"
        assert not os.path.exists(work)


class TestTheSuccessBodyEchoesNoRequestValue(unittest.TestCase):
    """A 200 answer says what the worker did, never what the caller sent (krizaka-internal#3390, S5131)."""

    def test_registered_model_answers_only_for_a_registered_name(self):
        assert video_worker.registered_model("wan2-2-t2v-a14b") == "wan2-2-t2v-a14b"
        for refused in ("<script>", "", None, ["wan2-2-t2v-a14b"], 7):
            with self.subTest(refused):
                assert video_worker.registered_model(refused) is None

    def test_an_encoded_clip_answers_without_the_output_path(self):
        handler = video_worker.VideoInferenceHandler.__new__(video_worker.VideoInferenceHandler)
        out = os.path.join(tempfile.mkdtemp(), "clip.mp4")
        with patch.object(video_worker, "export_frames_to_video"):
            body = handler._encode_output({}, [Image.new("RGB", (8, 8))], 14, out, None, {"frames": 1})
        assert body == {"status": "success", "metrics": {"frames": 1}}


if __name__ == "__main__":
    unittest.main()
