package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.ports.outbound.StructuredOutputBounds;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureStructuredBoundManager;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link StructuredOutputBounds} adapter (ADR-029, #169): reads {@code
 * orazaka_feature_structured_bounds} through the {@link FeatureStructuredBoundManager} inbound
 * port, so {@code sovkora-core} stays free of any persistence import. With no row at all (not even
 * the seeded default) the safe default of the port applies.
 */
@Service
class FeatureStructuredBoundsAdapter implements StructuredOutputBounds {

  private final FeatureStructuredBoundManager bounds;

  FeatureStructuredBoundsAdapter(FeatureStructuredBoundManager bounds) {
    this.bounds = Objects.requireNonNull(bounds, "FeatureStructuredBoundManager cannot be null");
  }

  @Override
  public int maxAttempts(String featureKey) {
    return bounds.maxAttempts(featureKey).orElse(DEFAULT_MAX_ATTEMPTS);
  }
}
