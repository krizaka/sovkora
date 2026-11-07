package com.krizaka.sovkora.core.domain.model;

import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State machine context holding user and system matrices for prompt refinement and routing.
 *
 * <p>Implemented as an immutable Java 21 record in compliance with ADR-008.
 *
 * @param rawUserQuery The original raw user query prompt.
 * @param userMetadata Immutable map containing enriched user attributes and preferences.
 * @param systemMetadata Immutable map containing enriched system and environment signals.
 * @param refinedPrompt The refined instruction text (defaults to rawUserQuery).
 * @param routedProvider The routed model provider key (or null if default).
 * @param routingMode The active routing strategy (DETERMINISTIC or AGENTIC).
 * @param mediaParts How many non-text parts travel to the model with this turn (ADR-063).
 */
public record PromptContext(
    String rawUserQuery,
    Map<String, Object> userMetadata,
    Map<String, Object> systemMetadata,
    String refinedPrompt,
    String routedProvider,
    RoutingMode routingMode,
    int mediaParts) {

  /**
   * Compact constructor enforcing immutability and defensive copying.
   *
   * @param rawUserQuery The original raw user query prompt.
   * @param userMetadata Immutable map containing enriched user attributes and preferences.
   * @param systemMetadata Immutable map containing enriched system and environment signals.
   * @param refinedPrompt The refined instruction text (defaults to rawUserQuery).
   * @param routedProvider The routed model provider key (or null if default).
   * @param routingMode The active routing strategy (DETERMINISTIC or AGENTIC).
   * @param mediaParts How many non-text parts travel to the model with this turn.
   */
  public PromptContext {
    userMetadata = sanitizeMap(userMetadata);
    systemMetadata = sanitizeMap(systemMetadata);
    if (routingMode == null) {
      routingMode = RoutingMode.DETERMINISTIC;
    }
    if (mediaParts < 0) {
      throw new IllegalArgumentException("mediaParts must be >= 0, was: " + mediaParts);
    }
  }

  /**
   * A text-only context: nothing but the query travels to the model.
   *
   * @param rawUserQuery The original raw user query prompt.
   * @param userMetadata Immutable map containing enriched user attributes and preferences.
   * @param systemMetadata Immutable map containing enriched system and environment signals.
   * @param refinedPrompt The refined instruction text.
   * @param routedProvider The routed model provider key (or null if default).
   * @param routingMode The active routing strategy.
   */
  public PromptContext(
      String rawUserQuery,
      Map<String, Object> userMetadata,
      Map<String, Object> systemMetadata,
      String refinedPrompt,
      String routedProvider,
      RoutingMode routingMode) {
    this(rawUserQuery, userMetadata, systemMetadata, refinedPrompt, routedProvider, routingMode, 0);
  }

  /**
   * Overloaded constructor for initial creation of PromptContext.
   *
   * @param rawUserQuery The raw user query prompt.
   * @param userMetadata Initial user context matrix.
   */
  public PromptContext(String rawUserQuery, Map<String, Object> userMetadata) {
    this(rawUserQuery, userMetadata, Map.of(), rawUserQuery, null, RoutingMode.DETERMINISTIC);
  }

  /**
   * Whether the text this pipeline reads is not the whole turn.
   *
   * <p>The engine lifts media out of the prompt before the pipeline runs, so every interceptor
   * reads text only — and on a media turn the text can be "what do you think?" while the subject is
   * a photographed lease. An interceptor that judges or rewrites the turn must ask this before it
   * trusts the text: a guard that cannot see an image is in the room is not guarding the turn
   * (ADR-063). The engine only states the fact; what a media turn means is the interceptor's call.
   *
   * @return {@code true} when at least one non-text part travels with this turn
   */
  public boolean carriesMedia() {
    return mediaParts > 0;
  }

  /**
   * Returns a copy of this context with a new refined prompt.
   *
   * @param refinedPrompt The refined prompt text.
   * @return A new PromptContext instance.
   */
  public PromptContext withRefinedPrompt(String refinedPrompt) {
    return new PromptContext(
        rawUserQuery,
        userMetadata,
        systemMetadata,
        refinedPrompt,
        routedProvider,
        routingMode,
        mediaParts);
  }

  /**
   * Returns a copy of this context with a new routed provider.
   *
   * @param routedProvider The routed provider key.
   * @return A new PromptContext instance.
   */
  public PromptContext withRoutedProvider(String routedProvider) {
    return new PromptContext(
        rawUserQuery,
        userMetadata,
        systemMetadata,
        refinedPrompt,
        routedProvider,
        routingMode,
        mediaParts);
  }

  /**
   * Returns a copy of this context with a new routing mode.
   *
   * @param routingMode The routing mode strategy.
   * @return A new PromptContext instance.
   */
  public PromptContext withRoutingMode(RoutingMode routingMode) {
    return new PromptContext(
        rawUserQuery,
        userMetadata,
        systemMetadata,
        refinedPrompt,
        routedProvider,
        routingMode,
        mediaParts);
  }

  /**
   * Returns a copy of this context with a new user metadata map.
   *
   * @param userMetadata The new user metadata map.
   * @return A new PromptContext instance.
   */
  public PromptContext withUserMetadata(Map<String, Object> userMetadata) {
    return new PromptContext(
        rawUserQuery,
        userMetadata,
        systemMetadata,
        refinedPrompt,
        routedProvider,
        routingMode,
        mediaParts);
  }

  /**
   * Returns a copy of this context with a new system metadata map.
   *
   * @param systemMetadata The new system metadata map.
   * @return A new PromptContext instance.
   */
  public PromptContext withSystemMetadata(Map<String, Object> systemMetadata) {
    return new PromptContext(
        rawUserQuery,
        userMetadata,
        systemMetadata,
        refinedPrompt,
        routedProvider,
        routingMode,
        mediaParts);
  }

  /**
   * System-metadata key under which the entitlement gate records its credit reservation. Private:
   * the record reads and writes its own payload (ERR-127), so no caller handles the raw key.
   */
  private static final String BILLING_HOLD_ID_KEY = "billing.holdId";

  /**
   * Returns a copy of this context carrying the credit reservation taken for the turn.
   *
   * <p>The hold rides in the context because the gate that takes it runs in the pipeline while the
   * settlement happens after inference — this is what carries it across the gap (ADR-033 §6.2).
   *
   * @param holdId The reservation identifier.
   * @return A new PromptContext instance.
   */
  public PromptContext withBillingHold(String holdId) {
    var metadata = new HashMap<>(systemMetadata);
    metadata.put(BILLING_HOLD_ID_KEY, holdId);
    return withSystemMetadata(metadata);
  }

  /**
   * The credit reservation taken for this turn.
   *
   * @return The hold identifier, or {@code null} when the turn was not metered.
   */
  public String billingHoldId() {
    return systemMetadata.get(BILLING_HOLD_ID_KEY) instanceof String s && !s.isBlank() ? s : null;
  }

  /** System-metadata key under which a cache records the answer it found (read by the engine). */
  private static final String CACHED_RESPONSE_KEY = "cache.response";

  /**
   * Returns a copy of this context carrying an answer found before inference. The engine then skips
   * the model; the output screens still run on the answer, and the turn's hold is released.
   *
   * @param response the cached answer
   * @return A new PromptContext instance.
   */
  public PromptContext withCachedResponse(CachedResponse response) {
    var metadata = new HashMap<>(systemMetadata);
    metadata.put(CACHED_RESPONSE_KEY, response);
    return withSystemMetadata(metadata);
  }

  /**
   * The answer a cache found for this turn, if any.
   *
   * @return the cached answer, or empty when the turn must be inferred
   */
  public Optional<CachedResponse> cachedResponse() {
    return systemMetadata.get(CACHED_RESPONSE_KEY) instanceof CachedResponse c
        ? Optional.of(c)
        : Optional.empty();
  }

  /**
   * System-metadata key under which the grounding stage records the documents it put in front of
   * the model, so the verifier checks citations against those and nothing else (ADR-087 §3).
   */
  private static final String RESEARCH_READ_KEY = "research.read";

  /**
   * Returns a copy of this context carrying the documents grounded research read for this turn.
   *
   * @param read the documents, each by its source, URL and content hash
   * @return A new PromptContext instance.
   */
  public PromptContext withResearchRead(List<ReadDocument> read) {
    var metadata = new HashMap<>(systemMetadata);
    metadata.put(RESEARCH_READ_KEY, List.copyOf(read));
    return withSystemMetadata(metadata);
  }

  /**
   * The documents grounded research read for this turn: a citation verifies against these only.
   *
   * @return the documents, empty when nothing was read for this turn
   */
  public List<ReadDocument> researchRead() {
    if (systemMetadata.get(RESEARCH_READ_KEY) instanceof List<?> read) {
      return read.stream()
          .filter(ReadDocument.class::isInstance)
          .map(ReadDocument.class::cast)
          .toList();
    }
    return List.of();
  }

  /**
   * Sanitizes a map by removing null keys and values, then wrapping it in an immutable copy.
   *
   * @param input The map to sanitize.
   * @return A new immutable map containing only non-null entries.
   */
  private static Map<String, Object> sanitizeMap(Map<String, Object> input) {
    if (input == null || input.isEmpty()) {
      return Map.of();
    }
    var clean = new HashMap<String, Object>();
    input.forEach(
        (k, v) -> {
          if (k != null && v != null) {
            clean.put(k, v);
          }
        });
    return Map.copyOf(clean);
  }
}
