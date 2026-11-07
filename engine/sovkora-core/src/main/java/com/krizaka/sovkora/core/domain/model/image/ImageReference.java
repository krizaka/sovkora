package com.krizaka.sovkora.core.domain.model.image;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * A picture the generated image must stay faithful to: the same character, prop or place across
 * every shot of a production (krizaka-internal#158).
 *
 * <p>Self-validating (ERR-106): what no backend could use is refused here, at the boundary — an
 * empty or oversized file, a type that is not a still picture, a weight outside {@code [0, 1]}.
 *
 * @param image the picture's bytes (PNG, JPEG or WebP)
 * @param mediaType its media type, one of {@link #MEDIA_TYPES}
 * @param weight how strongly the image should follow it, {@code 0} to {@code 1}; a model that
 *     conditions at full strength only records the weight and says so with the image
 */
public record ImageReference(byte[] image, String mediaType, double weight) {

  /** The still formats every image engine of the platform decodes. */
  public static final Set<String> MEDIA_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

  // @fixed: a reference is a picture, not a video or an archive; 8 MiB holds a 4K PNG.
  public static final int MAX_BYTES = 8 * 1024 * 1024;

  /** The weight of a reference whose caller named none: follow it fully. */
  public static final double FULL = 1.0;

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public ImageReference {
    Objects.requireNonNull(image, "image must not be null");
    if (image.length == 0) {
      throw new IllegalArgumentException("a reference image must not be empty");
    }
    if (image.length > MAX_BYTES) {
      throw new IllegalArgumentException(
          "a reference image must be at most " + MAX_BYTES + " bytes, was " + image.length);
    }
    mediaType = mediaType == null ? "" : mediaType.strip().toLowerCase(java.util.Locale.ROOT);
    if (!MEDIA_TYPES.contains(mediaType)) {
      throw new IllegalArgumentException(
          "a reference image must be PNG, JPEG or WebP, was '" + mediaType + "'");
    }
    if (Double.isNaN(weight) || weight < 0 || weight > 1) {
      throw new IllegalArgumentException("a reference weight must be between 0 and 1");
    }
    image = image.clone();
  }

  @Override
  public byte[] image() {
    return image.clone();
  }

  /**
   * @return whether the weight asks for less than full strength
   */
  public boolean partial() {
    return weight < FULL;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ImageReference(byte[] otherImage, String otherType, double otherWeight)
        && Arrays.equals(image, otherImage)
        && mediaType.equals(otherType)
        && Double.compare(weight, otherWeight) == 0;
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(image) + mediaType.hashCode()) + Double.hashCode(weight);
  }

  @Override
  public String toString() {
    return "ImageReference[" + image.length + " bytes, " + mediaType + ", weight=" + weight + "]";
  }
}
