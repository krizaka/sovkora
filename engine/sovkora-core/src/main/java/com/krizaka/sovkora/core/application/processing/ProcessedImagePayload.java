package com.krizaka.sovkora.core.application.processing;

import java.util.Objects;

/**
 * Immutable payload carrying processed image data and its dimensions.
 *
 * <p>Compact constructor enforces null-safety and dimensional invariants per ADR-007.
 *
 * @param base64Image The base64-encoded image data.
 * @param width The image width in pixels.
 * @param height The image height in pixels.
 */
public record ProcessedImagePayload(String base64Image, int width, int height) {

  /** Compact constructor enforcing null-safety and positive dimensions. */
  public ProcessedImagePayload {
    base64Image = Objects.requireNonNullElse(base64Image, "");
    if (width < 0) {
      throw new IllegalArgumentException("Image width must be non-negative, got: " + width);
    }
    if (height < 0) {
      throw new IllegalArgumentException("Image height must be non-negative, got: " + height);
    }
  }
}
