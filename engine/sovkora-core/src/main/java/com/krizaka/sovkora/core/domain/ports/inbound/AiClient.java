package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.audio.AudioResponse;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import reactor.core.publisher.Flux;

/**
 * Public Inbound Port interface (Facade) for developers to interact with the Sovkora AI Ecosystem.
 *
 * <p>Exposes completely homogeneous, symmetric operations for all modalities: Text, Image, Audio, .
 */
public interface AiClient {

  /**
   * Executes a synchronous chat completion.
   *
   * @param request The public chat request.
   * @return The chat response.
   */
  ChatResponse chat(ChatRequest request);

  /**
   * Creates a reactive token stream for real-time chat completion.
   *
   * @param request The public chat request.
   * @return A {@link Flux} emitting incremental {@link ChatResponse} chunks.
   */
  Flux<ChatResponse> stream(ChatRequest request);

  /**
   * Generates speech audio from text prompt.
   *
   * @param request The speech/audio generation request parameters.
   * @return The generated audio response containing raw audio bytes and format.
   */
  AudioResponse audio(AudioRequest request);

  /**
   * Generates an image based on the prompt.
   *
   * @param request The image generation request parameters.
   * @return The generated image response.
   */
  ImageResponse image(ImageRequest request);
}
