package com.krizaka.sovkora.core.domain.model.structured;

import java.util.Objects;

/**
 * Asks a model for an answer that is an instance of {@code type} (pure Java: no framework type).
 *
 * @param systemPrompt the instruction, may be empty
 * @param userPrompt the question
 * @param type the shape the answer must have
 * @param featureKey the feature asking (its attempt bound is read by it), {@code null} for none
 * @param <T> the shape
 */
public record StructuredOutputRequest<T>(
    String systemPrompt, String userPrompt, Class<T> type, String featureKey) {

  /** A request that belongs to no feature (the default bound applies). */
  public StructuredOutputRequest(String systemPrompt, String userPrompt, Class<T> type) {
    this(systemPrompt, userPrompt, type, null);
  }

  /** Validates the required fields. */
  public StructuredOutputRequest {
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    Objects.requireNonNull(userPrompt, "userPrompt");
    Objects.requireNonNull(type, "type");
  }
}
