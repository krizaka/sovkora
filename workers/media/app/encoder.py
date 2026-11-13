import os
import platform
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Callable

from PIL import Image

from app import command_inputs

try:
    from diffusers.utils import export_to_video
except ImportError:
    export_to_video = None


def is_apple_silicon() -> bool:
    """Detect if running on an Apple Silicon (macOS arm64) host."""
    return sys.platform == "darwin" and platform.machine() == "arm64"


#: The name ffmpeg writes in the worker's own temp directory before the clip moves to its destination.
_ENCODED = "encoded.mp4"


def export_frames_to_video(
    frames: list[Image.Image],
    output_path: str,
    fps: object = 7,
    *,
    codec: str | None = None,
    run: Callable[..., object] | None = None,
) -> None:
    """Encodes the frames into an MP4 at ``output_path``.

    h264_videotoolbox on Apple Silicon, libx264 elsewhere, diffusers' ``export_to_video`` when ffmpeg fails.
    Every value is checked before ffmpeg runs (app.command_inputs): the destination inside the worker's
    directories, the frame rate a bounded number, the codec on the allow-list. ffmpeg only ever names files
    in a temp directory this call made; the clip is moved to the destination once encoded.
    """
    if not frames:
        raise ValueError("Frames list must not be empty.")
    from app import main  # deferred: main imports this module

    target = command_inputs.destination(output_path, main._allowed_roots())
    rate = command_inputs.frame_rate(fps)
    chosen = command_inputs.video_codec(codec) if codec is not None else _platform_codec()

    work = tempfile.mkdtemp()
    try:
        for idx, frame in enumerate(frames):
            frame.save(os.path.join(work, f"frame_{idx:04d}.png"), format="PNG")
        encoded = os.path.join(work, _ENCODED)
        # High-bitrate H.264: -b:v 8M for dense output, yuv420p for players, +faststart for web streaming.
        cmd = [
            "ffmpeg",
            "-y",
            "-framerate",
            str(rate),
            "-i",
            os.path.join(work, "frame_%04d.png"),
            "-c:v",
            chosen.value,
            "-b:v",
            "8M",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            encoded,
        ]
        print(f"Executing encoder: {' '.join(cmd)}", flush=True)
        (run or subprocess.run)(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        shutil.move(encoded, target)
        print("FFmpeg encoding completed successfully.", flush=True)
        return
    except (OSError, subprocess.SubprocessError) as failed:
        print(
            f"FFmpeg encoder failed or command not found: {failed}. Falling back to diffusers export_to_video.",
            flush=True,
        )
    finally:
        shutil.rmtree(work, ignore_errors=True)

    if export_to_video is None:
        raise RuntimeError("No suitable video exporter found.")
    export_to_video(frames, target, fps=rate)


def _platform_codec() -> command_inputs.VideoCodec:
    return command_inputs.VideoCodec.H264_VIDEOTOOLBOX if is_apple_silicon() else command_inputs.VideoCodec.LIBX264
