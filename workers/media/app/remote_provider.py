"""The port of provider-served video: what the worker asks of any provider that makes a clip for it.

A model with ``pipeline: remote`` names its provider (``provider`` in ``models.yaml`` and in the catalogue
row); ``app.remote_providers.provider_for`` turns that name into one adapter behind this port. The worker's
``run_remote`` knows only the port: the aspect, the seed, the metering, the sealed asset and the provenance
are the same whoever makes the pixels.

§12 Zero-Fallback: a provider that cannot honour a request (a starting image it has no use for, a duration
outside its range) raises. It never answers a different clip.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Protocol

from app.video_models import VideoModel

#: The progress callback of a run: a percentage, 0-95 while the provider works.
Progress = Callable[[int], None]


class RemoteVideoProvider(Protocol):
    """One provider's way of making one clip. Returns the MP4 bytes and the facts about the call."""

    def generate(
        self,
        model: VideoModel,
        prompt: str,
        width: int,
        height: int,
        seed: int | None,
        progress: Progress | None = None,
        *,
        image: bytes | None = None,
        seconds: int | None = None,
        **injected,
    ) -> tuple[bytes, dict]:
        """Submits, waits and downloads one clip.

        ``image`` is the starting frame of an ``imageToVideo`` model (PNG bytes); ``seconds`` the length asked.
        ``injected`` carries the clock, the sleeper and the transport of a test; production passes none.
        """
        ...
