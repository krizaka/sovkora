"""Which adapter serves a provider-served video model: chosen by the model's ``provider``, never by its name."""

from __future__ import annotations

from app import remote_video, runpod_video
from app.remote_provider import RemoteVideoProvider
from app.video_models import InvalidVideoRequest, VideoModel

#: The providers this worker can speak to, by the name the catalogue row (``ai_providers``) gives them.
PROVIDERS: dict[str, RemoteVideoProvider] = {
    remote_video.PROVIDER: remote_video.DigitalOceanVideoProvider(),
    runpod_video.PROVIDER: runpod_video.RunpodVideoProvider(),
}


def provider_for(model: VideoModel) -> RemoteVideoProvider:
    """The adapter of ``model``'s provider, or a refusal by name: a provider nobody declared is never guessed."""
    adapter = PROVIDERS.get(model.provider or "")
    if adapter is None:
        known = ", ".join(sorted(PROVIDERS))
        raise InvalidVideoRequest(f"model '{model.name}' names the provider '{model.provider}'; this worker speaks {known}")
    return adapter
