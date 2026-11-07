package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;

/** Outbound port to retrieve tool configuration properties dynamically. */
public interface PlatformToolConfigProvider {

  record PlatformToolConfig(
      Integer id,
      String toolId,
      Boolean cacheEnabled,
      Integer cacheTtlSeconds,
      Boolean ragEnabled,
      String chunkerType,
      String sourceTable) {}

  Optional<PlatformToolConfig> getToolConfig(String toolId);
}
