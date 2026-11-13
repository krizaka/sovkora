"""The values that reach an ffmpeg argument, parsed at the edge into closed types (krizaka-internal#3390).

A request value never goes on a command line as it came. A frame rate is a bounded number, a codec is
one of an allow-list, and a destination is a path resolved inside the worker's own directories. Even
then, the destination is not an argument: the command writes a file in the worker's temp directory and
the clip is moved to the checked destination afterwards.
"""

from __future__ import annotations

import math
import os
from collections.abc import Iterable
from enum import StrEnum

from app.video_models import InvalidVideoRequest

MIN_FRAME_RATE = 1
MAX_FRAME_RATE = 120


class UnsafeCommandInput(InvalidVideoRequest):
    """A value that would reach a command and is not one this worker passes on. Refused, never repaired."""


class VideoCodec(StrEnum):
    """The H.264 encoders this worker runs: the Apple Silicon hardware one and the software one."""

    H264_VIDEOTOOLBOX = "h264_videotoolbox"
    LIBX264 = "libx264"


def video_codec(value: str) -> VideoCodec:
    """The codec named by ``value``, or a refusal when it is not on the allow-list."""
    try:
        return VideoCodec(value)
    except ValueError as off_list:
        raise UnsafeCommandInput("the codec is not one this worker encodes with") from off_list


def frame_rate(value: object) -> int | float:
    """A frame rate between ``MIN_FRAME_RATE`` and ``MAX_FRAME_RATE``, as a number (an int when whole).

    A JSON number or a numeric string is accepted. A boolean, a value starting with ``-``, and anything
    that is not a finite number in bounds are refused.
    """
    if isinstance(value, str) and value.strip().startswith("-"):
        raise UnsafeCommandInput("a value starting with '-' never reaches a command")
    if isinstance(value, bool) or not isinstance(value, int | float | str):
        raise UnsafeCommandInput("the frame rate is not a number")
    try:
        rate = float(value)
    except ValueError as not_a_number:
        raise UnsafeCommandInput("the frame rate is not a number") from not_a_number
    if not math.isfinite(rate) or not MIN_FRAME_RATE <= rate <= MAX_FRAME_RATE:
        raise UnsafeCommandInput(
            f"the frame rate must be between {MIN_FRAME_RATE} and {MAX_FRAME_RATE}"
        )
    return int(rate) if rate.is_integer() else rate


def destination(path: str, roots: Iterable[str]) -> str:
    """``path`` resolved (symlinks and ``..`` followed) when it lands under one of ``roots``.

    Raises ``UnsafeCommandInput`` for an empty path or a name starting with ``-``, and
    ``PermissionError`` for a path outside the roots. Neither message repeats the path.
    """
    if not path:
        raise UnsafeCommandInput("the output path is empty")
    if path.startswith("-") or os.path.basename(path).startswith("-"):
        raise UnsafeCommandInput("a value starting with '-' never reaches a command")
    resolved = os.path.realpath(os.path.abspath(path))
    for root in roots:
        if resolved.startswith(os.path.realpath(root) + os.sep):
            return resolved
    raise PermissionError("the output path is outside the worker's directories")
