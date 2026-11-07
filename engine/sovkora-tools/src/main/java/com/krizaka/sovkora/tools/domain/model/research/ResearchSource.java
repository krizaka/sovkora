package com.krizaka.sovkora.tools.domain.model.research;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An administrator-declared source of grounded research ({@code research_source}, ADR-087 §2). A
 * fetch only reads what {@link #allowPatterns} allow and {@link #denyPatterns} do not deny.
 *
 * @param id the row id
 * @param sourceKey the stable key
 * @param kind {@code SEARCH}, {@code FETCH}, {@code OFFICIAL}, {@code CODE_HOST} or {@code MCP}
 * @param provider the provider value (a vendor is a value, never a type)
 * @param allowPatterns host[/path] globs; empty allows nothing
 * @param denyPatterns host[/path] globs checked after the allow list
 * @param allowHttp whether plain http is read (https otherwise)
 * @param freshnessSeconds how long a document read through this source is served from the cache
 * @param ratePerMinute fetches per minute through this source, per instance
 * @param enabled whether the source is on
 */
public record ResearchSource(
    UUID id,
    String sourceKey,
    String kind,
    String provider,
    List<String> allowPatterns,
    List<String> denyPatterns,
    boolean allowHttp,
    int freshnessSeconds,
    int ratePerMinute,
    boolean enabled) {

  /** Validates and copies the patterns. */
  public ResearchSource {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    allowPatterns = allowPatterns == null ? List.of() : List.copyOf(allowPatterns);
    denyPatterns = denyPatterns == null ? List.of() : List.copyOf(denyPatterns);
    if (freshnessSeconds < 0 || ratePerMinute <= 0) {
      throw new IllegalArgumentException("freshness must be >= 0 and rate > 0");
    }
  }
}
