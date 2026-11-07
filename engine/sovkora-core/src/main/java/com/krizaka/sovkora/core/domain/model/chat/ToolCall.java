package com.krizaka.sovkora.core.domain.model.chat;

import java.util.Objects;

/**
 * A tool call the model asked for, which the engine returns to the client instead of running it
 * (ADR-093 §4). The arguments are the model's own words: data, never trusted.
 *
 * @param id the id the model gave the call; the client's result names it back
 * @param name the tool the model called
 * @param argumentsJson the arguments as the model wrote them
 */
public record ToolCall(String id, String name, String argumentsJson) {

  /** Compact constructor: a name is required, a missing id or arguments are empty. */
  public ToolCall {
    Objects.requireNonNull(name, "name must not be null");
    id = id == null ? "" : id;
    argumentsJson = argumentsJson == null ? "" : argumentsJson;
  }
}
