package com.krizaka.sovkora.adapter.springai.routing;

import com.krizaka.sovkora.core.domain.model.MediaCategory;
import com.krizaka.sovkora.core.infrastructure.provider.model.ModelUnavailableException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

/**
 * Primary {@link ChatModel} bean, backed by the DB-driven {@link DynamicModelRouter} (ADR-030).
 *
 * <p>Replaces the former static {@code AiModelConfiguration.activeChatModel} bean (built from
 * {@code application.yml}). The model is resolved <b>per call</b>: the prompt's requested model
 * name (when present) routes to that model's DB endpoint, otherwise the category's catalog default
 * ({@code is_default}) is used. So an admin edit to {@code ai_providers} / {@code orazaka_models}
 * takes effect with no redeploy and no Spring AI starter / autoconfiguration.
 *
 * @see DynamicModelRouter
 * @see com.krizaka.sovkora.core.infrastructure.provider.model.CatalogModelEndpointResolver
 */
public class RoutedChatModel implements ChatModel {

  private final DynamicModelRouter router;

  public RoutedChatModel(DynamicModelRouter router) {
    this.router = router;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    try {
      return resolve(prompt).call(outgoing(prompt));
    } catch (RuntimeException e) {
      String model = requestedModel(prompt);
      if (model != null && isUnreachable(e)) {
        throw new ModelUnavailableException(model, router.describe(model), e);
      }
      throw e;
    }
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    String model = requestedModel(prompt);
    Flux<ChatResponse> stream = resolve(prompt).stream(outgoing(prompt));
    return model == null
        ? stream
        : stream.onErrorMap(
            RoutedChatModel::isUnreachable,
            e -> new ModelUnavailableException(model, router.describe(model), e));
  }

  private ChatModel resolve(Prompt prompt) {
    return requestedModel(prompt) != null
        ? router.chatModel(requestedModel(prompt))
        : router.defaultChatModel(MediaCategory.CHAT.value());
  }

  /** A provider that cannot currently serve the model, not an invalid or unauthorized request. */
  static boolean isUnreachable(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof java.net.ConnectException
          || t instanceof java.net.UnknownHostException
          || t instanceof java.net.NoRouteToHostException
          || t instanceof java.net.SocketTimeoutException
          || t instanceof java.net.http.HttpTimeoutException) {
        return true;
      }
      int status =
          switch (t) {
            case OpenAIServiceException response -> response.statusCode();
            case RestClientResponseException response -> response.getStatusCode().value();
            default -> 0;
          };
      if (status == 502 || status == 503 || status == 504) {
        return true;
      }
    }
    return false;
  }

  private static String requestedModel(Prompt prompt) {
    String model = prompt.getOptions() != null ? prompt.getOptions().getModel() : null;
    return model != null && !model.isBlank() ? model : null;
  }

  /**
   * The prompt sent on the wire. A request without a model takes the catalogue default, named
   * explicitly: converted options left without a model would carry Spring AI's own default ({@code
   * OpenAiChatOptions.builder()} fills {@code gpt-5-mini}) to whatever provider serves the default
   * — a 403 from a hosted provider that does not serve it.
   */
  private Prompt outgoing(Prompt prompt) {
    Prompt converted = toOpenAiPrompt(prompt);
    if (requestedModel(prompt) != null) {
      return converted;
    }
    String model = router.defaultModelName(MediaCategory.CHAT.value());
    OpenAiChatOptions options =
        converted.getOptions() instanceof OpenAiChatOptions openAi
            ? openAi.mutate().model(model).build()
            : OpenAiChatOptions.builder().model(model).build();
    return new Prompt(converted.getInstructions(), options);
  }

  /**
   * Coerces the prompt's runtime options to {@link OpenAiChatOptions}. The router always resolves
   * an OpenAI-compatible model ({@link UniversalProxyChatProvider} → {@code OpenAiChatModel}),
   * whose {@code createRequest} casts the runtime options to {@code OpenAiChatOptions}. Upstream
   * pipeline stages tag local providers (e.g. Ollama reached via its {@code /v1} endpoint) with
   * {@code OllamaChatOptions}, which would otherwise trigger a {@link ClassCastException}
   * (ADR-030). Null or already-OpenAi options pass through untouched.
   */
  static Prompt toOpenAiPrompt(Prompt prompt) {
    ChatOptions options = prompt.getOptions();
    if (options == null || options instanceof OpenAiChatOptions) {
      return prompt;
    }
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder()
            .model(options.getModel())
            .temperature(options.getTemperature())
            .maxTokens(options.getMaxTokens());
    if (options instanceof ToolCallingChatOptions toolOptions) {
      // getToolCallbacks() is nullable (e.g. OllamaChatOptions returns null, not an empty list) —
      // guard before isEmpty() to avoid an NPE that would break every non-tool chat stream.
      var toolCallbacks = toolOptions.getToolCallbacks();
      if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
        builder.toolCallbacks(toolCallbacks);
      }
    }
    return new Prompt(prompt.getInstructions(), builder.build());
  }
}
