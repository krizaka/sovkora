package com.krizaka.sovkora.business.api;

import java.util.Objects;

/**
 * Payload for a {@link Capability#IMAGE} intention.
 *
 * @param prompt The image generation prompt (required, non-blank).
 * @param size Optional target size hint (e.g. {@code "1024x1024"}); may be null.
 */
public record ImagePayload(String prompt, String size) implements Payload {
  public ImagePayload {
    Objects.requireNonNull(prompt, "prompt must not be null");
    if (prompt.isBlank()) {
      throw new IllegalArgumentException("Image prompt must not be blank");
    }
  }
}
