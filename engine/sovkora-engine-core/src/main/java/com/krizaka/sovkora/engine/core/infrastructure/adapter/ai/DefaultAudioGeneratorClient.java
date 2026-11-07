package com.krizaka.sovkora.engine.core.infrastructure.adapter.ai;

import com.krizaka.sovkora.engine.core.application.engine.Engine;
import com.krizaka.sovkora.engine.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.engine.core.domain.model.audio.AudioResponse;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.AudioGeneratorClient;
import java.util.Objects;

/**
 * Outbound adapter implementing {@link AudioGeneratorClient} by delegating speech generation to the
 * core Engine.
 */
public class DefaultAudioGeneratorClient implements AudioGeneratorClient {

  private final Engine engine;

  public DefaultAudioGeneratorClient(Engine engine) {
    this.engine = Objects.requireNonNull(engine, "Engine must not be null");
  }

  @Override
  public AudioResponse generateAudio(AudioRequest request) {
    byte[] data = engine.generateSpeech(request);
    // The format the caller declared is the format it gets; mp3 stays the engine's default.
    return new AudioResponse(
        data, request.responseFormat() == null ? "mp3" : request.responseFormat());
  }
}
