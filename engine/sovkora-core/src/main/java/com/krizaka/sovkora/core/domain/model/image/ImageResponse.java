package com.krizaka.sovkora.core.domain.model.image;

import java.util.Arrays;
import java.util.Objects;

/**
 * Unified image response record.
 *
 * @param imageData The raw binary data of the generated image (may be null if URL is used).
 * @param url The public URL of the generated image.
 * @param format The image file format (e.g., "png", "jpg").
 * @param usage What the generation consumed, for settlement (ADR-047); never null.
 * @param notice What the engine could not honour as asked (e.g. a reference weight a model applies
 *     at full strength), said with the image; {@code null} when everything was honoured.
 */
public record ImageResponse(
    byte[] imageData, String url, String format, ImageUsage usage, String notice) {

  /**
   * Compact canonical constructor: an absent usage is the "nothing reported" sentinel, not null.
   */
  public ImageResponse {
    usage = usage == null ? ImageUsage.none() : usage;
    notice = notice == null || notice.isBlank() ? null : notice.strip();
  }

  /**
   * A measured image with nothing to report beyond it.
   *
   * @param imageData the raw bytes
   * @param url the public URL
   * @param format the file format
   * @param usage what the generation consumed
   */
  public ImageResponse(byte[] imageData, String url, String format, ImageUsage usage) {
    this(imageData, url, format, usage, null);
  }

  /**
   * An image whose generation parameters are unknown.
   *
   * <p>Kept as a three-argument constructor because only one production site builds a measured
   * response, while every fallback and test builds an unmeasured one — and an unmeasured image
   * releases its hold rather than billing a guess.
   *
   * @param imageData the raw bytes
   * @param url the public URL
   * @param format the file format
   */
  public ImageResponse(byte[] imageData, String url, String format) {
    this(imageData, url, format, ImageUsage.none(), null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o
        instanceof
        ImageResponse(
            byte[] otherData,
            String otherUrl,
            String otherFormat,
            ImageUsage otherUsage,
            String otherNotice))) {
      return false;
    }
    return Arrays.equals(imageData, otherData)
        && Objects.equals(url, otherUrl)
        && Objects.equals(format, otherFormat)
        && Objects.equals(usage, otherUsage)
        && Objects.equals(notice, otherNotice);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(imageData);
    result = 31 * result + Objects.hashCode(url);
    result = 31 * result + Objects.hashCode(format);
    result = 31 * result + Objects.hashCode(usage);
    return 31 * result + Objects.hashCode(notice);
  }

  @Override
  public String toString() {
    int dataLen = imageData != null ? imageData.length : 0;
    return "ImageResponse[imageData="
        + dataLen
        + " bytes, url="
        + url
        + ", format="
        + format
        + ", usage="
        + usage
        + ", notice="
        + notice
        + "]";
  }
}
