package com.krizaka.sovkora.tools.domain.model.research;

import java.util.List;
import java.util.Objects;

/**
 * The fetch settings in force for one call, read from {@code orazaka_runtime_config} at call time
 * (the administrator changes them without a restart).
 *
 * @param timeoutMs connect + read limit
 * @param maxBytes largest body read
 * @param maxRedirects redirect hops followed
 * @param perHostPerMinute fetches per host and minute
 * @param contentTypes the media types read
 * @param userAgent the honest user agent
 * @param defaultFreshnessSeconds cache lifetime when the source sets none
 */
public record FetchLimits(
    int timeoutMs,
    int maxBytes,
    int maxRedirects,
    int perHostPerMinute,
    List<String> contentTypes,
    String userAgent,
    int defaultFreshnessSeconds) {

  /** Validates the limits. */
  public FetchLimits {
    Objects.requireNonNull(userAgent, "userAgent must not be null");
    contentTypes = contentTypes == null ? List.of() : List.copyOf(contentTypes);
    if (timeoutMs <= 0 || maxBytes <= 0 || maxRedirects < 0 || perHostPerMinute <= 0) {
      throw new IllegalArgumentException("fetch limits must be positive");
    }
  }

  /**
   * The product token of the user agent (what robots.txt groups match on).
   *
   * @return the token, e.g. {@code SovkoraResearch}
   */
  public String productToken() {
    String first = userAgent.trim().split("[/\\s]", 2)[0];
    return first.isEmpty() ? "SovkoraResearch" : first;
  }
}
