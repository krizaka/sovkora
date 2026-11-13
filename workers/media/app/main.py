#!/usr/bin/env python3
import base64
import html
import io
import json
import os
import sys
import tempfile
import threading
from enum import StrEnum
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any

from PIL import Image

try:
    import psutil
except ImportError:
    psutil = None

try:
    import torch
except ImportError:
    torch = None

from app import job_control, remote_video, stages, upscale, video_models
from app.encoder import export_frames_to_video
from app.generator import generate_video_frames
from app.telemetry import resource_guard, send_progress_update
from app.wan_generator import generate_wan_frames, is_resident

try:
    from app.mlx_generator import generate_video_frames_mlx
except ImportError:
    generate_video_frames_mlx = None

MODEL_REGISTRY: dict[str, str] = {
    "stable-video-diffusion-img2vid-xt": "SVD",
    "animatediff-lightning-mps": "AnimateDiff",
    "apple-coreml-video-pipeline": "CoreML",
    "mlx-animatediff-lightning": "MLX-AnimateDiff",
    "mlx-stable-diffusion-video": "MLX-SVD",
    "stable-video-diffusion-img2vid-xt-mps-fp32": "SVD-MPS-FP32",
    # MLX Community HuggingFace models
    "argmaxinc/mlx-FLUX.1-schnell-4bit-quantized": "MLX-FLUX-Schnell",
    "ByteDance/AnimateDiff-Lightning": "MLX-AnimateDiff-Lightning",
    # Wan 2.x on CUDA (diffusers) — the 9:16 clips of the GPU droplet (models.yaml, ADR-077).
    "wan2.2-ti2v-5b": "Wan",
    "wan2.1-t2v-1.3b": "Wan",
    "wan2.2-t2v-a14b": "Wan",  # the 720p A14B, run here on an 80 GB card (models.yaml `hero`)
    "ltx-video-0.9.8-13b-distilled": "Wan",  # "Wan" is this worker's label for the diffusers video generators (app/wan_generator.py): LTX is one
    "wan2.2-i2v-a14b": "Wan",
    "wan2.2-i2v-a14b-hq": "Wan",  # the same checkpoint at its full 40-step schedule (the bake-off's winner for people in 9:16)  # the shots of a cinematic short, from their keyframes (models.yaml `cinema`, ADR-080)
    # Served by a provider, routed by Orazaka (app/remote_video.py): nothing runs on this host.
    "wan2-2-t2v-a14b": "Remote",
}

# ── Host-memory guard (per user mandate) ──────────────────────────────────────
# Approximate peak unified-memory footprint per model (GB). The preflight uses
# this to FAIL oversized models fast and cleanly instead of letting them swap or
# freeze the host. Data-driven default selection is done in the router from the
# catalog; this is the worker's conservative, self-contained safety net.
MODEL_MEMORY_GB: dict[str, int] = {
    "stable-video-diffusion-img2vid-xt": 96,
    "stable-video-diffusion-img2vid-xt-mps-fp32": 96,
    "animatediff-lightning-mps": 6,
    "mlx-animatediff-lightning": 6,
    "ByteDance/AnimateDiff-Lightning": 6,
    "mlx-stable-diffusion-video": 8,
    "apple-coreml-video-pipeline": 4,
    "argmaxinc/mlx-FLUX.1-schnell-4bit-quantized": 6,
    # Host RAM, not VRAM: what the offloaded components occupy while the card runs the rest.
    "wan2.2-ti2v-5b": 24,
    "wan2.1-t2v-1.3b": 16,
    "wan2.2-t2v-a14b": 64,  # two bf16 experts (~57 GB) + the text encoder, offloaded
    "wan2.2-i2v-a14b": 64,  # the same two experts, image to video
}
_DEFAULT_MODEL_GB = 12  # conservative estimate for unregistered models

# Serialize heavy media work: at most one model resident / one inference at a time.
_media_semaphore = threading.Semaphore(1)

#: What the pipeline answers a job its owner stopped (nginx's "client closed request"): not a failure.
CANCELLED_STATUS = 499


class ErrorCode(StrEnum):
    """The ``error`` of every refusal the HTTP pipeline answers: a closed set the caller can branch on."""

    NOT_FOUND = "not_found"
    MODEL_NOT_REGISTERED = "model_not_registered"
    INVALID_REQUEST = "invalid_request"
    CANCELLED = "cancelled"
    GENERATION_FAILED = "generation_failed"


#: What `_answer` writes for the characters HTML gives a meaning to: their JSON escapes, which decode to themselves.
_JSON_HTML_ESCAPES = str.maketrans({"<": "\\u003c", ">": "\\u003e", "&": "\\u0026"})

#: The fixed ``detail`` of each code. A refusal of the request itself (INVALID_REQUEST) says the
#: worker's own reason instead, HTML-escaped; an exception or provider message is only ever logged.
_FIXED_DETAIL: dict[ErrorCode, str] = {
    ErrorCode.NOT_FOUND: "the media worker has no such route",
    ErrorCode.MODEL_NOT_REGISTERED: "the model is not registered on this worker",
    ErrorCode.INVALID_REQUEST: "the request cannot be honoured",
    ErrorCode.CANCELLED: "the job was cancelled by its owner",
    ErrorCode.GENERATION_FAILED: "the video could not be made; the worker's log has the cause",
}


def registered_model(name: object) -> str | None:
    """The registry's own name for ``name``, or None when it is not registered.

    The name used afterwards is the registry's key, never the caller's string: the model a request
    named is parsed into one of the worker's own values at the edge (krizaka-internal#3390).
    """
    return next((known for known in MODEL_REGISTRY if known == name), None)


def preflight_memory_guard(model_name: str) -> None:
    """Fail fast/clean if the model cannot fit available unified memory.

    Compares psutil.virtual_memory().available against the model's estimated
    footprint plus a configurable headroom (ORAZAKA_MEDIA_MEM_HEADROOM_GB,
    default 14). Raises RuntimeError -> the job is reported FAILED with a clear
    reason. §12 Zero-Fallback: never fake output, never swap/freeze the machine.
    """
    headroom_gb = float(os.environ.get("ORAZAKA_MEDIA_MEM_HEADROOM_GB", "14"))
    est_gb = MODEL_MEMORY_GB.get(model_name, _DEFAULT_MODEL_GB)
    if psutil is None:
        print("[mem-guard] psutil unavailable — skipping preflight.", flush=True)
        return
    vm = psutil.virtual_memory()
    avail_gb = vm.available / (1024**3)
    # Optional: also bail under acute memory pressure (>92% used).
    if vm.percent >= 92.0:
        raise RuntimeError(
            f"host under high memory pressure ({vm.percent:.0f}% used); refusing to "
            f"load model '{model_name}'. Free memory or pick a lighter model."
        )
    if est_gb > (avail_gb - headroom_gb):
        raise RuntimeError(
            f"model '{model_name}' needs ~{est_gb} GB but only {avail_gb:.1f} GB is "
            f"available (headroom {headroom_gb:.0f} GB); pick a lighter model such as "
            f"'animatediff-lightning-mps'"
        )
    print(
        f"[mem-guard] model='{model_name}' est={est_gb}GB avail={avail_gb:.1f}GB "
        f"headroom={headroom_gb:.0f}GB used={vm.percent:.0f}% -> OK",
        flush=True,
    )


def _upload_roots() -> list:
    """Resolve the monorepo upload directory (``UPLOAD_DIR``) to absolute root(s).

    The router writes generated media under ``<workspace>/var/orazaka-uploads``
    (see ``VideoGenerationStrategy`` / ``JobMediaHelper``) and hands the worker an
    absolute ``output_path`` there. The worker is spawned with a cwd that is NOT
    guaranteed to be the monorepo root, so anchoring the allow-list on cwd alone
    rejects legitimate writes ("outside allowed directories"). We anchor instead
    on this file's location (``app/main.py`` -> 4 levels up = monorepo root) and
    honor an absolute ``UPLOAD_DIR`` override. Only the uploads dir is allowed —
    not the whole workspace — so directory traversal stays blocked.
    """
    upload_dir = os.environ.get("UPLOAD_DIR", "var/orazaka-uploads")
    if os.path.isabs(upload_dir):
        candidates = [upload_dir]
    else:
        worker_root = os.path.join(os.path.dirname(__file__), "..", "..", "..", "..")
        candidates = [
            os.path.join(worker_root, upload_dir),
            os.path.join(os.getcwd(), upload_dir),
        ]
    return [os.path.abspath(os.path.realpath(c)) for c in candidates]


def _allowed_roots() -> list:
    """The directories the worker may read and write media in: the working directory, the temp directories and the uploads."""
    roots = [os.getcwd(), tempfile.gettempdir(), *_upload_roots()]
    return roots + [t for t in ("/tmp", "/private/tmp", "/var/tmp") if os.path.exists(t)]  # noqa: S108


def validate_safe_path(path: str) -> str:
    """Validates that a path is safe to access, preventing directory traversal."""
    if not path:
        raise ValueError("Path is empty")

    # Resolve to absolute normalized path
    resolved_path = os.path.abspath(os.path.realpath(path))

    # Check if the resolved path is within allowed directories: the one allow-list, resolved the same way
    allowed_roots = [os.path.abspath(os.path.realpath(root)) for root in _allowed_roots()]

    is_safe = False
    for root in allowed_roots:
        try:
            if os.path.commonpath([root, resolved_path]) == root:
                is_safe = True
                break
        except ValueError:
            continue

    if not is_safe:
        raise PermissionError(f"Access to path '{path}' is denied (outside allowed directories).")

    return resolved_path


def load_image(payload: dict[str, Any]) -> Image.Image:
    """Ingests starting image from base64 data or a local path, falling back to a default."""
    img = Image.new("RGB", (1024, 576), (128, 128, 128))
    if payload.get("image_path"):
        try:
            path = payload["image_path"]
            safe_path = validate_safe_path(path)
            if os.path.exists(safe_path):
                return Image.open(safe_path).convert("RGB").resize((1024, 576))
        except Exception as e:
            print(f"Could not open image_path: {e}", flush=True)
    elif payload.get("image"):
        try:
            img_data = base64.b64decode(payload["image"])
            return Image.open(io.BytesIO(img_data)).convert("RGB").resize((1024, 576))
        except Exception as e:
            print(f"Could not parse base64 image: {e}", flush=True)
    return img


def _asked_for_an_image(payload: dict[str, Any]) -> bool:
    """Whether the caller gave a starting image (blank strings are the Studio's empty input)."""
    return any(str(payload.get(key) or "").strip() for key in ("image_path", "image"))


def _focus(payload: dict[str, Any]) -> tuple:
    """Where the subject is in the photo, ``(x, y)`` in 0..1 (``focusX``/``focusY``), centre by default."""

    def one(key: str) -> float:
        try:
            return min(1.0, max(0.0, float(payload.get(key))))
        except (TypeError, ValueError):
            return 0.5

    return one("focusX"), one("focusY")


def starting_image(payload: dict[str, Any]) -> Image.Image:
    """The photo a clip starts from, or a refusal: never a grey stand-in (§12).

    ``image_path`` (a file under the upload directory or a temp directory) wins; ``image`` is the
    base64 of the file. An image that cannot be read is the caller's to fix, not a reason to animate grey.
    """
    try:
        if str(payload.get("image_path") or "").strip():
            return Image.open(validate_safe_path(payload["image_path"])).convert("RGB")
        return Image.open(io.BytesIO(base64.b64decode(payload["image"]))).convert("RGB")
    except Exception as unreadable:
        raise video_models.InvalidVideoRequest(
            f"the starting image cannot be read ({type(unreadable).__name__})"
        ) from unreadable


def _generate_flux_image(
    prompt: str,
    model_name: str,
    steps: int,
    num_frames: int,
    seed: int,
) -> list:
    """Generate image frames using FLUX.1 via mflux (MLX native).

    Falls back to a prompt-colored gradient image if mflux is not installed.
    Returns a list of PIL Images suitable for video encoding.
    """
    try:
        from mflux import Config, Flux1

        print(f"Initializing FLUX via mflux: model={model_name}, steps={steps}, seed={seed}", flush=True)
        flux = Flux1(
            model_alias="schnell",
            quantize=4,
        )
        image = flux.generate_image(
            seed=seed,
            prompt=prompt,
            config=Config(
                num_inference_steps=steps,
                height=576,
                width=1024,
            ),
        )
        pil_image = image.image
        print(f"FLUX inference completed: {pil_image.size}", flush=True)
        # Return duplicated frames for video encoding compatibility
        return [pil_image.copy() for _ in range(num_frames)]

    except ImportError as exc:
        raise RuntimeError("mflux is not installed. Cannot generate FLUX image without the dependency.") from exc
    except Exception as e:
        raise RuntimeError(f"FLUX inference failed: {e}") from e


def _record_consumption(metrics, frames, steps, fps, req_width, req_height) -> None:
    """Record what was actually produced, for the settlement of this job's hold.

    Measurements only — no billable unit. Which unit a render bills in belongs
    to the pricebook row its hold was pinned to and is resolved by the billing
    service (design §7); a worker that named a unit would be a second copy of a
    pricing decision it does not own.

    Every value is the *realised* one, not the requested one: the pipelines snap
    steps to the variants Lightning supports and cap frames to what MPS memory
    allows, and billing a user for the render they asked for rather than the one
    they got is how a credit system loses trust.
    """
    try:
        if frames:
            metrics["frames"] = len(frames)
            metrics["images"] = len(frames)
            width, height = frames[0].size
            metrics["width"] = int(width)
            metrics["height"] = int(height)
        elif req_width and req_height:
            metrics["width"] = int(req_width)
            metrics["height"] = int(req_height)
        if steps:
            metrics["steps"] = int(steps)
        if fps:
            metrics["fps"] = int(fps)
    except Exception as e:
        # Metering must never fail a render that already succeeded; an absent
        # measurement releases the hold, which errs toward the user.
        print(f"Failed to record consumption metrics: {e}", flush=True)


def apply_upscale(payload: dict[str, Any], path: str, metrics: dict[str, Any]) -> None:
    """The Studio's optional `upscale` step: the finished clip at `path` is replaced by its 1080p/4K version, and the
    metrics say what was done (model, from, to, seconds) and the size the clip now has."""
    target = upscale.requested(payload)
    if not target:
        return
    facts = upscale.upscale(path, target, _allowed_roots())
    metrics.update(facts)
    width, height = facts["upscaleTo"].split("x")
    metrics["width"], metrics["height"] = int(width), int(height)


def _gpu_name() -> str | None:
    """The accelerator this host ran on, for the facts kept with a clip."""
    try:
        if torch is not None and torch.cuda.is_available():
            return str(torch.cuda.get_device_name(0))
        if torch is not None and torch.backends.mps.is_available():
            return "Apple Silicon (Metal)"
    except Exception:  # noqa: BLE001
        return None
    return None


def _record_provenance(metrics, model_name, seed, aspect, frames, fps) -> None:
    """How a clip was made, kept with it: what a caption, a re-run or a cost report needs.

    ``durationSeconds`` is the duration PRODUCED (frames / fps), which is what OUTPUT_SECOND bills —
    never the duration asked for, which Wan rounds to its 4k+1 frame grid.
    """
    metrics["model"] = model_name
    metrics["seed"] = str(seed)
    if aspect:
        metrics["aspect"] = video_models.normalise_aspect(aspect)
    gpu = _gpu_name()
    if gpu:
        metrics["gpu"] = gpu
    try:
        if frames and fps:
            metrics["durationSeconds"] = round(len(frames) / float(fps), 2)
    except (TypeError, ValueError, ZeroDivisionError):
        pass


def _write_inside_uploads(path: str, data: bytes) -> str:
    """Writes a clip under the upload root, and nowhere else (the provider's bytes are the only thing written)."""
    real = os.path.realpath(path)
    for root in _upload_roots():
        if real.startswith(root + os.sep):
            os.makedirs(os.path.dirname(real), exist_ok=True)
            with open(real, "wb") as out:
                out.write(data)
            return real
    raise PermissionError(f"Access to path '{path}' is denied (outside the upload directory).")


def run_remote(
    payload: dict[str, Any], model_name: str, process=None, generate=remote_video.generate
) -> dict[str, Any]:
    """One clip from a provider-served model, as the response body of the pipeline.

    The shape is what Wan on a GPU gives (aspect → size, seed, metering, provenance); only the pixels come
    from elsewhere, and ``metrics.provider`` says so.
    """
    model = video_models.find(model_name)
    if payload.get("image") or payload.get("image_path"):
        raise video_models.InvalidVideoRequest(
            f"model '{model_name}' makes video from text only; leave the starting image empty"
        )
    prompt = (payload.get("prompt") or "").strip()
    width, height = model.size_for(payload.get("aspect"), payload.get("width"), payload.get("height"))
    seed_text = str(payload.get("seed") or os.environ.get("ORAZAKA_VIDEO_GEN_SEED", "-1"))
    try:
        seed_val = int(seed_text)
    except ValueError:
        seed_val = -1
    job_id = payload.get("job_id")
    with resource_guard(process) as metrics:
        if job_id:
            send_progress_update(job_id, 0, phase="generating")

        def progress(percent):
            if job_id:
                job_control.raise_if_cancelled(job_id)
                send_progress_update(job_id, percent, phase="generating")

        video, facts = generate(model, prompt, width, height, seed_val if seed_val >= 0 else None, progress=progress)
        metrics.update(facts)
        metrics["model"] = model_name
        metrics["seed"] = str(seed_val) if seed_val >= 0 else "provider"
        metrics["aspect"] = video_models.normalise_aspect(payload.get("aspect"))
        metrics["width"], metrics["height"], metrics["fps"] = width, height, model.fps
        output_path = payload.get("output_path")
        if not output_path:
            return {"data": [{"b64_json": base64.b64encode(video).decode("ascii")}], "metrics": metrics}
        _write_inside_uploads(validate_safe_path(output_path), video)
        measured = remote_video.probe_duration(video)
        if measured:
            metrics["durationSeconds"] = measured
        if job_id:
            send_progress_update(job_id, 100, phase="uploading")
        return {"status": "success", "metrics": metrics}


class VideoInferenceHandler(BaseHTTPRequestHandler):
    def _answer(self, status: int, body: dict[str, Any]) -> None:
        """The one way this handler answers: a JSON body, its charset, and no content sniffing.

        ``<``, ``>`` and ``&`` leave as JSON escapes: the bytes never hold markup, the decoded data is the same.
        """
        content = json.dumps(body).translate(_JSON_HTML_ESCAPES).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def _refuse(self, status: int, code: ErrorCode) -> None:
        self._answer(status, {"error": code.value, "detail": _FIXED_DETAIL[code]})

    def _refuse_request(self, bad: video_models.InvalidVideoRequest) -> None:
        """A request the worker checked and cannot honour: its own reason, escaped, since it may quote the request."""
        print(f"Refused video request: {bad}", flush=True)
        self._answer(
            400,
            {
                "error": ErrorCode.INVALID_REQUEST.value,
                "detail": html.escape(str(bad), quote=False),
            },
        )

    def _fail(self, payload: dict[str, Any], failure: Exception) -> None:
        """An unexpected failure: the cause goes to the log with the job id, the caller gets a fixed message."""
        print(
            f"Error during video generation (job {payload.get('job_id')!r}): {failure!r}",
            flush=True,
        )
        self._refuse(500, ErrorCode.GENERATION_FAILED)

    def _serve_remote(self, payload: dict[str, Any], model_name: str, process) -> None:
        try:
            with _media_semaphore:
                body = run_remote(payload, model_name, process)
        except video_models.InvalidVideoRequest as bad:
            self._refuse_request(bad)
        except job_control.JobCancelled:
            self._refuse(CANCELLED_STATUS, ErrorCode.CANCELLED)
        except Exception as failure:  # noqa: BLE001 — §12: the provider's refusal or failure is the job's failure
            self._fail(payload, failure)
        else:
            self._answer(200, body)

    def _read_payload(self) -> dict[str, Any]:
        """The request body as a JSON object; anything else reads as an empty request, as it always has."""
        try:
            length = max(0, int(self.headers.get("Content-Length", 0)))
            payload = json.loads(self.rfile.read(length).decode("utf-8")) if length else {}
        except (ValueError, UnicodeDecodeError):
            return {}
        return payload if isinstance(payload, dict) else {}

    def do_GET(self) -> None:
        if self.path == "/":
            self._answer(200, {"status": "running"})
        else:
            self._refuse(404, ErrorCode.NOT_FOUND)

    def do_POST(self) -> None:
        if self.path != "/v1/videos/generations":
            self._refuse(404, ErrorCode.NOT_FOUND)
            return

        payload = self._read_payload()
        model_name = registered_model(payload.get("model") or video_models.default_model_name())
        if model_name is None:
            self._refuse(400, ErrorCode.MODEL_NOT_REGISTERED)
            return

        try:
            upscale.requested(payload)  # a value nobody can make is refused before a GPU second is spent
        except video_models.InvalidVideoRequest as bad:
            self._refuse_request(bad)
            return

        process = psutil.Process(os.getpid()) if psutil else None
        if MODEL_REGISTRY[model_name] == "Remote":
            self._serve_remote(payload, model_name, process)
            return
        input_image = load_image(payload)
        try:
            with _media_semaphore, resource_guard(process) as metrics:
                seed_val, generator = self._resolve_seed(payload)
                job_id = payload.get("job_id")
                family = MODEL_REGISTRY[model_name]

                # Host-memory preflight: refuse oversized models BEFORE loading any
                # weights, so SVD-XT fails fast/clean on a 64GB Mac instead of
                # swapping/freezing the host (§12: honest failure, never fake output).
                # A Wan pipeline already loaded in this process has paid for its memory: the guard
                # measures what is FREE, and would refuse the second clip because the first one's
                # pipeline is resident (found on the GPU droplet: every clip after the first failed).
                if not is_resident(model_name, _asked_for_an_image(payload)):
                    preflight_memory_guard(model_name)

                steps, fps_render, wan, wan_image = self._resolve_model_params(payload, model_name, family)

                if job_id:
                    job_control.raise_if_cancelled(job_id)
                    send_progress_update(job_id, 0, phase="loading")

                stage_reporter = stages.reporter_for(job_id, model_name)

                def progress_cb(pipe_self: Any, step: int, timestep: Any, kwargs: dict[str, Any]) -> dict[str, Any]:
                    if job_id:
                        # The step boundary: where a cancelled job ends, within one denoising step.
                        job_control.raise_if_cancelled(job_id)
                        progress = min(int((step + 1) / steps * 100), 100)
                        send_progress_update(job_id, progress, phase="generating", step=step + 1, steps=int(steps))
                        if stage_reporter:
                            stage_reporter.step(step + 1, int(steps))
                    return kwargs

                frames, req_width, req_height = self._run_generation(
                    payload,
                    model_name,
                    family,
                    wan,
                    wan_image,
                    input_image,
                    steps,
                    fps_render,
                    seed_val,
                    generator,
                    stage_reporter,
                    progress_cb,
                )

                _record_consumption(metrics, frames, steps, fps_render, req_width, req_height)
                _record_provenance(metrics, model_name, seed_val, payload.get("aspect"), frames, fps_render)
                if family == "Wan" and wan_image is not None:
                    # How much of the starting image the frame kept (a portrait still cut to 16:9 keeps a third): said, never hidden.
                    metrics["imageKept"] = video_models.kept_fraction(wan_image, int(req_width), int(req_height))

                output_path = payload.get("output_path")
                if job_id:
                    job_control.raise_if_cancelled(job_id)
                    send_progress_update(job_id, 100, phase="encoding")
                    if stage_reporter:
                        stage_reporter.verify()
                response_data = self._encode_output(payload, frames, fps_render, output_path, job_id, metrics)

        except job_control.JobCancelled as stopped:
            print(f"Video generation stopped: {stopped}", flush=True)
            self._refuse(CANCELLED_STATUS, ErrorCode.CANCELLED)
        except video_models.InvalidVideoRequest as bad:
            self._refuse_request(bad)
        except Exception as failure:  # noqa: BLE001 — every failure answers, none leaves the client hanging
            self._fail(payload, failure)
        else:
            self._answer(200, response_data)

    def _resolve_seed(self, payload: dict[str, Any]):
        """Returns ``(seed_val, generator)`` — a concrete seed and the torch generator for this request."""
        # The caller's seed first, so a clip can be made again; then the deployment's.
        seed_str = str(payload.get("seed") or os.environ.get("ORAZAKA_VIDEO_GEN_SEED", "-1"))
        try:
            seed_val = int(seed_str)
        except ValueError:
            seed_val = -1
        if seed_val == -1:
            import secrets

            # 0..2147483647, the range torch's seed takes; printed below so a clip can be made again.
            seed_val = secrets.randbelow(2**31)
        print(f"Using video generation seed: {seed_val}", flush=True)
        generator = torch.manual_seed(seed_val) if torch else None
        return seed_val, generator

    def _resolve_model_params(self, payload: dict[str, Any], model_name: str, family: str):
        """Resolves steps, fps, the Wan model descriptor and the starting image. Returns ``(steps, fps_render, wan, wan_image)``."""
        steps = payload.get("videoSteps") or payload.get("video_steps")
        fps_render = payload.get("videoFps") or payload.get("video_fps")

        wan = video_models.find(model_name) if family == "Wan" else None
        wan_image = None
        if wan is not None and _asked_for_an_image(payload):
            if not wan.image_to_video:
                # A starting image the model would ignore is not the run that was asked for (§12).
                raise video_models.InvalidVideoRequest(
                    f"model '{model_name}' makes video from text only; leave the starting "
                    "image empty or choose an image-to-video model"
                )
            wan_image = starting_image(payload)
        elif wan is not None and wan.requires_image:
            raise video_models.InvalidVideoRequest(
                f"model '{model_name}' starts from a photo: give it a starting image (a keyframe)"
            )
        if not steps and wan is not None:
            steps = wan.steps
        if not fps_render and wan is not None:
            fps_render = wan.fps

        if not steps:
            model_lower = model_name.lower()
            if "flux" in model_lower:
                steps = 4  # FLUX Schnell distilled
            elif "animatediff" in model_lower:
                steps = 16  # Deep inference for 64GB Unified Memory
            else:
                steps = 25

        if not fps_render:
            model_lower = model_name.lower()
            if "animatediff" in model_lower:
                fps_render = 12
            elif "flux" in model_lower:
                fps_render = 1  # FLUX generates still images
            else:
                fps_render = 14

        return steps, fps_render, wan, wan_image

    def _run_generation(
        self,
        payload: dict[str, Any],
        model_name: str,
        family: str,
        wan,
        wan_image,
        input_image,
        steps,
        fps_render,
        seed_val: int,
        generator,
        stage_reporter,
        progress_cb,
    ):
        """Runs the appropriate generation backend and returns ``(frames, req_width, req_height)``."""
        duration = payload.get("durationSeconds") or payload.get("video_length") or 2
        # Resolution from payload (defaults handled by mlx_generator)
        req_width = payload.get("width")
        req_height = payload.get("height")

        # MLX models: minimum 16 frames for cinematic output (4.0s at 4fps)
        # The text-to-video AnimateDiff pipeline, by the family the registry declares — the
        # catalogue default `animatediff-lightning-mps` used to fall through to SVD's
        # image-to-video branch from a grey still (orazaka-worker-media#3).
        is_mlx_model = (
            family in ("AnimateDiff", "MLX-AnimateDiff", "MLX-AnimateDiff-Lightning")
            or model_name.startswith("mlx-")
            or model_name.startswith("mlx-community/")
        )
        min_frames = 16 if is_mlx_model else 14
        frames_n = max(min_frames, min(int(duration * fps_render), 30))

        # Allow explicit num_frames override from payload (backward-compatible control pathway)
        explicit_frames = payload.get("num_frames")
        if explicit_frames and int(explicit_frames) > 0:
            frames_n = int(explicit_frames)

        # The Studio's aspect ("9:16" by default) picks one of the sizes the model was
        # trained at; an explicit width and height still win (models.yaml, ADR-077).
        shaped = wan
        if shaped is None and payload.get("aspect"):
            shaped = video_models.video_models().get(model_name)
        if shaped is not None:
            req_width, req_height = shaped.size_for(payload.get("aspect"), req_width, req_height)
        if wan is not None:
            frames_n = wan.frames_for(duration)
            if explicit_frames and int(explicit_frames) > 0:
                frames_n = min(int(explicit_frames), wan.max_frames)

        frames = self._dispatch_generation_backend(
            payload,
            model_name,
            family,
            wan,
            wan_image,
            input_image,
            steps,
            fps_render,
            frames_n,
            seed_val,
            generator,
            req_width,
            req_height,
            stage_reporter,
            progress_cb,
            is_mlx_model,
        )

        # The size really made (a smaller one when the card could not hold the first).
        if family == "Wan" and frames:
            req_width, req_height = frames[0].size

        return frames, req_width, req_height

    def _dispatch_generation_backend(
        self,
        payload: dict[str, Any],
        model_name: str,
        family: str,
        wan,
        wan_image,
        input_image,
        steps,
        fps_render,
        frames_n: int,
        seed_val: int,
        generator,
        req_width,
        req_height,
        stage_reporter,
        progress_cb,
        is_mlx_model: bool,
    ):
        """Calls the right generation backend (Wan, FLUX, MLX AnimateDiff, or SVD) and returns the frames list."""
        if wan is not None:
            ladder = wan.size_ladder(payload.get("aspect"), payload.get("width"), payload.get("height"))
            return generate_wan_frames(
                model_name=model_name,
                prompt=payload.get("prompt", ""),
                width=int(req_width),
                height=int(req_height),
                num_frames=frames_n,
                steps=int(steps),
                seed=seed_val,
                negative_prompt=payload.get("negativePrompt") or None,
                progress_callback=progress_cb,
                weights_progress=stage_reporter.weights if stage_reporter else None,
                image=wan_image,
                focus=_focus(payload),
                fallback_sizes=tuple(ladder[1:]) if wan_image is not None else (),
            )
        if model_name.startswith("argmaxinc/"):
            # FLUX image generation via mflux (MLX native)
            return _generate_flux_image(
                prompt=payload.get("prompt", ""),
                model_name=model_name,
                steps=steps,
                num_frames=frames_n,
                seed=seed_val,
            )
        if is_mlx_model:
            if generate_video_frames_mlx is None:
                raise ImportError("AnimateDiff-Lightning pipeline is not available on this host.")
            mlx_kwargs = {
                "input_image": input_image,
                "num_frames": frames_n,
                "num_inference_steps": steps,
                "progress_callback": progress_cb,
                "prompt": payload.get("prompt", ""),
            }
            if req_width:
                mlx_kwargs["width"] = int(req_width)
            if req_height:
                mlx_kwargs["height"] = int(req_height)
            return generate_video_frames_mlx(**mlx_kwargs)
        return generate_video_frames(
            input_image=input_image,
            num_frames=frames_n,
            num_inference_steps=steps,
            generator=generator,
            progress_callback=progress_cb,
        )

    def _encode_output(
        self, payload: dict[str, Any], frames, fps_render, output_path, job_id, metrics: dict[str, Any]
    ) -> dict[str, Any]:
        """Encodes the generated frames to MP4 and returns the response dict (file path or base64)."""
        if output_path:
            safe_output_path = validate_safe_path(output_path)
            # Inline startswith guard (same pattern as _write_inside_uploads) so CodeQL
            # tracks the sanitization at this call site rather than inside validate_safe_path.
            for root in [os.path.realpath(os.path.abspath(r)) for r in _allowed_roots()]:
                if safe_output_path.startswith(root + os.sep):
                    os.makedirs(os.path.dirname(safe_output_path), exist_ok=True)
                    export_frames_to_video(frames, safe_output_path, fps=fps_render)
                    if job_id:
                        send_progress_update(job_id, 100, phase="upscaling")
                    apply_upscale(payload, safe_output_path, metrics)
                    return {"status": "success", "metrics": metrics}
            raise PermissionError(f"Access to path '{output_path}' is denied (outside allowed directories).")
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
            tmp_path = tmp.name
        try:
            export_frames_to_video(frames, tmp_path, fps=fps_render)
            with open(tmp_path, "rb") as f:
                video_bytes = f.read()
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        base64_video = base64.b64encode(video_bytes).decode("utf-8")
        if job_id:
            send_progress_update(job_id, 100)
        return {"data": [{"b64_json": base64_video}], "metrics": metrics}


def run(port: int = 8188) -> None:
    # Bind loopback by default; set MEDIA_VIDEO_HOST="" to bind all interfaces for explicit external access.
    host = os.environ.get("MEDIA_VIDEO_HOST", "127.0.0.1")
    server_address = (host, port)
    httpd = HTTPServer(server_address, VideoInferenceHandler)
    print(f"Video Worker listening on {host}:{port}...", flush=True)
    try:
        httpd.serve_forever()  # NOSONAR python:S5332 - internal HTTP server bound to 127.0.0.1 by default; HTTPS over loopback adds complexity with no security benefit
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == "__main__":
    port_arg = 8188
    if len(sys.argv) > 1:
        port_arg = int(sys.argv[1])
    run(port_arg)
