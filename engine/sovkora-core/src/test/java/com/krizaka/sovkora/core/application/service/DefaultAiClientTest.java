package com.krizaka.sovkora.core.application.service;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.audio.AudioResponse;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ImageGeneratorClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class DefaultAiClientTest {

  @Mock private ChatGeneratorClient chatGeneratorClient;
  @Mock private AudioGeneratorClient audioGeneratorClient;
  @Mock private ImageGeneratorClient imageGeneratorClient;

  private DefaultAiClient aiClient;

  @BeforeEach
  void setUp() {
    aiClient = new DefaultAiClient(chatGeneratorClient, audioGeneratorClient, imageGeneratorClient);
  }

  @Test
  void shouldDelegateChat() {
    ChatRequest request = new ChatRequest(PROMPT, List.of(), Map.of(), Context.anonymous());
    ChatResponse expectedResponse =
        new ChatResponse("reply", "conv-1", TokenUsage.none(), Map.of());
    when(chatGeneratorClient.generateChat(request)).thenReturn(expectedResponse);

    ChatResponse actualResponse = aiClient.chat(request);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(chatGeneratorClient).generateChat(request);
  }

  @Test
  void shouldDelegateStream() {
    ChatRequest request = new ChatRequest(PROMPT, List.of(), Map.of(), Context.anonymous());
    ChatResponse chunk = new ChatResponse("chunk", "conv-1", TokenUsage.none(), Map.of());
    when(chatGeneratorClient.streamChat(request)).thenReturn(Flux.just(chunk));

    List<ChatResponse> result = aiClient.stream(request).collectList().block();

    assertThat(result).containsExactly(chunk);
    verify(chatGeneratorClient).streamChat(request);
  }

  @Test
  void shouldDelegateAudio() {
    AudioRequest request =
        new AudioRequest(PROMPT, "alloy", "tts-1", Map.of(), Context.anonymous());
    AudioResponse expectedResponse = new AudioResponse(new byte[] {1, 2, 3}, "mp3");
    when(audioGeneratorClient.generateAudio(request)).thenReturn(expectedResponse);

    AudioResponse actualResponse = aiClient.audio(request);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(audioGeneratorClient).generateAudio(request);
  }

  @Test
  void shouldDelegateImage() {
    ImageRequest request =
        new ImageRequest(PROMPT, 512, 512, "model", Map.of(), Context.anonymous());
    ImageResponse expectedResponse = new ImageResponse(new byte[] {4, 5}, "url", "png");
    when(imageGeneratorClient.generateImage(request)).thenReturn(expectedResponse);

    ImageResponse actualResponse = aiClient.image(request);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(imageGeneratorClient).generateImage(request);
  }
}
