package com.krizaka.sovkora.business.api;

import java.util.List;
import java.util.Objects;

/**
 * Payload for a {@link Capability#CHAT} intention.
 *
 * @param prompt The user prompt (required, non-blank).
 * @param assetIds Optional referenced asset ids (multimodal context); never null.
 */
public record ChatPayload(String prompt, List<String> assetIds) implements Payload {
  public ChatPayload {
    Objects.requireNonNull(prompt, "prompt must not be null");
    if (prompt.isBlank()) {
      throw new IllegalArgumentException("Chat prompt must not be blank");
    }
    assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
  }
}
