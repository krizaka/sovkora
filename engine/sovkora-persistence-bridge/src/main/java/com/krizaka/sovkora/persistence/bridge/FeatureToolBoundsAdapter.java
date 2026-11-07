package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolLoopBounds;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureToolBoundManager;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link ToolLoopBounds} adapter (ADR-029, #169): reads {@code
 * orazaka_feature_tool_bounds} through the {@link FeatureToolBoundManager} inbound port, so {@code
 * sovkora-core} stays free of any persistence import. With no row at all (not even the seeded
 * default) the safe default of the port applies.
 */
@Service
class FeatureToolBoundsAdapter implements ToolLoopBounds {

  private final FeatureToolBoundManager bounds;

  FeatureToolBoundsAdapter(FeatureToolBoundManager bounds) {
    this.bounds = Objects.requireNonNull(bounds, "FeatureToolBoundManager cannot be null");
  }

  @Override
  public int maxRounds(String featureKey) {
    return bounds.maxToolRounds(featureKey).orElse(DEFAULT_MAX_ROUNDS);
  }
}
