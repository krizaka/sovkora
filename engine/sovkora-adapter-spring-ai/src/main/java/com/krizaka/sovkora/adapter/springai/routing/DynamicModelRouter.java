package com.krizaka.sovkora.adapter.springai.routing;

import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Dynamic Model Router (ADR-030).
 *
 * <p>Resolves a {@link ChatModel} <b>on demand from the DB</b>, replacing the static
 * {@code @Primary} model beans in the former {@code AiModelConfiguration}. Given a model name (the
 * request's chosen model, or a category's catalog default), it resolves the provider endpoint via
 * {@link ModelEndpointResolver} and builds an OpenAI-compatible client via the {@link
 * UniversalProxyChatProvider}. No Spring Boot starter / autoconfiguration is involved.
 *
 * <p>Built clients are cached per {@code (base-url, model)}. Because providers/models live in the
 * DB, an admin edit takes effect with no redeploy — call {@link #evictAll()} after such an edit (or
 * wire a short TTL).
 *
 * @see UniversalProxyChatProvider
 * @see ModelEndpointResolver
 */
public class DynamicModelRouter {

  private final ModelEndpointResolver resolver;
  private final UniversalProxyChatProvider proxy;
  private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

  private final SpendGuard spendGuard;

  public DynamicModelRouter(ModelEndpointResolver resolver, UniversalProxyChatProvider proxy) {
    this(resolver, proxy, SpendGuard.unlimited());
  }

  public DynamicModelRouter(
      ModelEndpointResolver resolver, UniversalProxyChatProvider proxy, SpendGuard spendGuard) {
    this.resolver = Objects.requireNonNull(resolver, "ModelEndpointResolver cannot be null");
    this.proxy = Objects.requireNonNull(proxy, "UniversalProxyChatProvider cannot be null");
    this.spendGuard = Objects.requireNonNull(spendGuard, "SpendGuard cannot be null");
  }

  /** Builds (or returns a cached) {@link ChatModel} for the given model name. */
  public ChatModel chatModel(String modelName) {
    return chatModelFor(
        resolver
            .resolve(modelName)
            .orElseThrow(
                () ->
                    new StepModelUnavailableException(
                        "No endpoint resolved for model: " + modelName)));
  }

  /** Builds (or returns a cached) {@link ChatModel} for a category's default model. */
  public ChatModel defaultChatModel(String category) {
    return chatModelFor(
        resolver
            .resolveDefault(category)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No default model endpoint resolved for category: " + category)));
  }

  /**
   * Builds (or returns a cached) {@link ChatModel} for a feature key's routing rows (ADR-029), the
   * category default when the feature has none.
   */
  public ChatModel featureChatModel(String featureKey, String fallbackCategory) {
    return chatModelFor(
        resolver
            .resolveForFeature(featureKey, fallbackCategory)
            .orElseThrow(
                () ->
                    new StepModelUnavailableException(
                        "No endpoint resolved for feature: " + featureKey)));
  }

  /** The name of a category's default model (catalogue {@code is_default}). */
  public String defaultModelName(String category) {
    return resolver
        .resolveDefault(category)
        .map(ModelEndpoint::modelName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No default model endpoint resolved for category: " + category));
  }

  /** Where the catalogue binds a model, for error messages: "provider 'x' at <base-url>". */
  public String describe(String modelName) {
    return resolver
        .resolve(modelName)
        .map(e -> "provider '" + e.providerName() + "' at " + e.baseUrl())
        .orElse("no provider");
  }

  /** Clears the client cache — call after an admin edits providers/models in the DB. */
  public void evictAll() {
    cache.clear();
  }

  private ChatModel chatModelFor(ModelEndpoint endpoint) {
    String key = endpoint.baseUrl() + "::" + endpoint.modelName();
    return cache.computeIfAbsent(
        key,
        ignored ->
            new GuardedChatModel(
                proxy.chatModel(endpoint.baseUrl(), endpoint.apiKey(), endpoint.modelName()),
                endpoint,
                spendGuard));
  }
}
