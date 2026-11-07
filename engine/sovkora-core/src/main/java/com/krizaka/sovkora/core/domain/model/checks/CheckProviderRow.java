package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Objects;
import java.util.Set;

/**
 * One provider row, admin data in the database (sovkora-engine#73). The routing is data: the {@code
 * handlerKey} names the adapter, never a class order or a name heuristic.
 *
 * @param provider the provider's name as the audit, the keys and the pricebook know it
 * @param handlerKey the adapter that serves it: {@code soquij}, {@code trustii}, {@code certn},
 *     {@code mock}
 * @param purposes what the row serves
 * @param regions the subject regions it serves (e.g. {@code QC}); empty serves any region
 * @param priority lower is tried first
 * @param enabled whether the admin switched it on
 * @param endpoint the provider's HTTPS endpoint, or {@code null} (the mock has none)
 * @param priceMicros the provider's price per search, in millionths of a Canadian dollar
 */
public record CheckProviderRow(
    String provider,
    String handlerKey,
    Set<CheckPurpose> purposes,
    Set<String> regions,
    int priority,
    boolean enabled,
    String endpoint,
    long priceMicros) {

  /** Validates the row and freezes its sets. */
  public CheckProviderRow {
    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("provider must not be blank");
    }
    if (handlerKey == null || handlerKey.isBlank()) {
      throw new IllegalArgumentException("handlerKey must not be blank");
    }
    purposes = Set.copyOf(Objects.requireNonNull(purposes, "purposes must not be null"));
    regions = Set.copyOf(Objects.requireNonNull(regions, "regions must not be null"));
    if (priceMicros < 0) {
      throw new IllegalArgumentException("priceMicros must not be negative");
    }
  }

  /**
   * Whether this row serves the purpose for a subject of the region.
   *
   * @param purpose what is asked
   * @param region the subject's region, or {@code null}
   * @return true when enabled, serving the purpose, and open to the region
   */
  public boolean serves(CheckPurpose purpose, String region) {
    return enabled
        && purposes.contains(purpose)
        && (regions.isEmpty() || (region != null && regions.contains(region)));
  }
}
