package com.krizaka.sovkora.business.api;

import java.util.Map;
import java.util.Objects;

/**
 * Payload for a {@link Capability#AGENT} intention — a goal the core's agent loop will plan and
 * execute (plan→act→observe). Business runs the plan; the intelligence lives in the core.
 *
 * @param goal The agent goal (required, non-blank).
 * @param params Optional structured parameters; never null.
 */
public record AgentPayload(String goal, Map<String, Object> params) implements Payload {
  public AgentPayload {
    Objects.requireNonNull(goal, "goal must not be null");
    if (goal.isBlank()) {
      throw new IllegalArgumentException("Agent goal must not be blank");
    }
    params = params == null ? Map.of() : Map.copyOf(params);
  }
}
