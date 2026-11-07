package com.krizaka.sovkora.core.application.pipeline;

import java.util.Base64;
import java.util.Optional;

/**
 * Extracts embedded Base64 media payloads from raw user queries.
 *
 * <p>Parses the marker format {@code [posterBase64: <data>]} from raw prompt strings and returns a
 * clean query with the media bytes separated. Enforces a maximum decoded payload size to prevent
 * OOM attacks from oversized Base64 inputs.
 */
public final class Base64MediaExtractor {

  /** Maximum decoded image size: 10 MB. */
  private static final int MAX_DECODED_SIZE = 10 * 1024 * 1024;

  /** Marker prefix for embedded Base64 media in prompt text. */
  private static final String MARKER = "[posterBase64: ";

  private Base64MediaExtractor() {}

  /**
   * Immutable extraction result containing the cleaned query and optional media bytes.
   *
   * @param cleanedQuery The user query with the Base64 marker removed.
   * @param imageBytes The decoded image bytes, or empty if no marker was found.
   */
  public record ExtractionResult(String cleanedQuery, Optional<byte[]> imageBytes) {}

  /**
   * Extracts Base64 media from the raw query string.
   *
   * <p>If the marker {@code [posterBase64: ...]} is found, extracts and decodes the Base64 segment.
   * The decoded payload is validated against the size limit ({@value MAX_DECODED_SIZE} bytes). If
   * decoding fails or the payload exceeds the limit, the original query is returned unchanged with
   * empty media bytes.
   *
   * @param rawQuery The raw user query potentially containing embedded media.
   * @return An {@link ExtractionResult} with the cleaned query and optional image bytes.
   */
  public static ExtractionResult extract(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return new ExtractionResult("", Optional.empty());
    }

    int markerIdx = rawQuery.indexOf(MARKER);
    if (markerIdx == -1) {
      return new ExtractionResult(rawQuery, Optional.empty());
    }

    int endIdx = rawQuery.indexOf("]", markerIdx);
    if (endIdx == -1) {
      return new ExtractionResult(rawQuery, Optional.empty());
    }

    String base64Data = rawQuery.substring(markerIdx + MARKER.length(), endIdx).trim();
    String cleanedQuery =
        (rawQuery.substring(0, markerIdx).trim() + " " + rawQuery.substring(endIdx + 1).trim())
            .trim();

    // Estimate decoded size before allocating (Base64 inflates ~33%)
    long estimatedSize = (long) (base64Data.length() * 0.75);
    if (estimatedSize > MAX_DECODED_SIZE) {
      return new ExtractionResult(cleanedQuery, Optional.empty());
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(base64Data);
      if (decoded.length > MAX_DECODED_SIZE) {
        return new ExtractionResult(cleanedQuery, Optional.empty());
      }
      return new ExtractionResult(cleanedQuery, Optional.of(decoded));
    } catch (IllegalArgumentException e) {
      return new ExtractionResult(cleanedQuery, Optional.empty());
    }
  }
}
