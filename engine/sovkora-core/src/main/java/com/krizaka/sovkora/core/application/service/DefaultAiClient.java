package com.krizaka.sovkora.core.application.service;

import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.audio.AudioResponse;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ImageGeneratorClient;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/** Concrete implementation of {@link AiClient}. */
public class DefaultAiClient implements AiClient {

  private static final Logger logger = LoggerFactory.getLogger(DefaultAiClient.class);

  private final ChatGeneratorClient chatGeneratorClient;
  private final AudioGeneratorClient audioGeneratorClient;
  private final ImageGeneratorClient imageGeneratorClient;

  public DefaultAiClient(
      ChatGeneratorClient chatGeneratorClient,
      AudioGeneratorClient audioGeneratorClient,
      ImageGeneratorClient imageGeneratorClient) {
    this.chatGeneratorClient =
        Objects.requireNonNull(chatGeneratorClient, "ChatGeneratorClient must not be null");
    this.audioGeneratorClient =
        Objects.requireNonNull(audioGeneratorClient, "AudioGeneratorClient must not be null");
    this.imageGeneratorClient =
        Objects.requireNonNull(imageGeneratorClient, "ImageGeneratorClient must not be null");
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    // The prompt is never logged: a log has no retention and no audit, and a SENSITIVE turn
    // owes its content both (ADR-064).
    logger.debug("AiClient chat invoked for conversation {}", request.context().conversationId());
    return chatGeneratorClient.generateChat(request);
  }

  @Override
  public Flux<ChatResponse> stream(ChatRequest request) {
    logger.debug(
        "AiClient stream chat invoked for conversation {}", request.context().conversationId());
    return chatGeneratorClient.streamChat(request);
  }

  @Override
  public AudioResponse audio(AudioRequest request) {
    logger.debug("AiClient audio generation invoked with model {}", request.model());
    return audioGeneratorClient.generateAudio(request);
  }

  @Override
  public ImageResponse image(ImageRequest request) {
    logger.debug("AiClient image generation invoked with model {}", request.model());
    return imageGeneratorClient.generateImage(request);
  }
}
