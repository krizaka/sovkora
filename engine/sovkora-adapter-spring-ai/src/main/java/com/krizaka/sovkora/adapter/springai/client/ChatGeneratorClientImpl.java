package com.krizaka.sovkora.adapter.springai.client;

import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatGeneratorClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * Outbound adapter implementing {@link ChatGeneratorClient} by delegating inference execution to
 * the core Engine.
 */
public class ChatGeneratorClientImpl implements ChatGeneratorClient {

  private final Engine engine;

  public ChatGeneratorClientImpl(Engine engine) {
    this.engine = Objects.requireNonNull(engine, "Engine must not be null");
  }

  @Override
  public ChatResponse generateChat(ChatRequest request) {
    InternalChatRequest internalReq = mapChatRequest(request, false);
    InternalChatResponse internalResp = engine.chat(internalReq);
    return mapChatResponse(internalResp);
  }

  @Override
  public Flux<ChatResponse> streamChat(ChatRequest request) {
    InternalChatRequest internalReq = mapChatRequest(request, true);
    return engine.stream(internalReq).map(this::mapChatResponse);
  }

  private static InternalChatRequest.ChatMessage mapChatMessage(ChatRequest.ChatMessage msg) {
    return new InternalChatRequest.ChatMessage(msg.role(), msg.content());
  }

  private static InternalChatRequest mapChatRequest(ChatRequest req, boolean streaming) {
    List<InternalChatRequest.ChatMessage> mappedMessages =
        req.messages().stream().map(ChatGeneratorClientImpl::mapChatMessage).toList();

    EngineChatOptions options = engineOptions(buildChatOptions(req.settings()));
    return new InternalChatRequest(req.prompt(), mappedMessages, options, req.context(), streaming);
  }

  /** The engine's view of the library options, which it keeps for the way back. */
  private static EngineChatOptions engineOptions(ChatOptions options) {
    return options == null
        ? null
        : new EngineChatOptions(
            options.getModel(), options.getTemperature(), options.getMaxTokens(), options);
  }

  private static ChatOptions buildChatOptions(Map<String, Object> settings) {
    if (settings == null || settings.isEmpty()) {
      return null;
    }
    String provider = (String) settings.get("provider");
    String model = (String) settings.get("model");
    Double temperature =
        settings.get("temperature") instanceof Number num ? num.doubleValue() : null;
    if (provider == null) {
      return buildModelOptions(model, temperature);
    }

    if ("ollama".equalsIgnoreCase(provider)) {
      return buildOllamaOptions(model, temperature);
    }
    if ("openai".equalsIgnoreCase(provider)) {
      return buildOpenAiOptions(model, temperature);
    }
    return buildModelOptions(model, temperature);
  }

  /**
   * A model named without (or with another) provider: kept as is. The catalogue binds every model
   * to its provider (RoutedChatModel routes by model), so the name is all routing needs.
   */
  private static ChatOptions buildModelOptions(String model, Double temperature) {
    if (model == null || model.isBlank()) {
      return null;
    }
    var builder = ChatOptions.builder().model(model);
    if (temperature != null) {
      builder.temperature(temperature);
    }
    return builder.build();
  }

  private static ChatOptions buildOllamaOptions(String model, Double temperature) {
    var builder = OllamaChatOptions.builder();
    if (model != null) {
      builder.model(model);
    }
    if (temperature != null) {
      builder.temperature(temperature);
    }
    return builder.build();
  }

  private static ChatOptions buildOpenAiOptions(String model, Double temperature) {
    var builder = OpenAiChatOptions.builder();
    if (model != null) {
      builder.model(model);
    }
    if (temperature != null) {
      builder.temperature(temperature);
    }
    return builder.build();
  }

  private ChatResponse mapChatResponse(InternalChatResponse resp) {
    // tokenUsage crosses the port. It was dropped here, which left every AiClient caller with no
    // measurement and made an asynchronous chat step unbillable (ADR-041).
    return new ChatResponse(
        resp.content(), resp.conversationId(), resp.tokenUsage(), resp.metadata());
  }
}
