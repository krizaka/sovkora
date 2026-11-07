package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformToolConfigPersistenceProvider;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Implementation of the outbound PlatformToolConfigProvider port using the app persistence
 * provider.
 */
@Service
class PlatformToolConfigProviderAdapter implements PlatformToolConfigProvider {

  private final PlatformToolConfigPersistenceProvider persistenceProvider;

  PlatformToolConfigProviderAdapter(PlatformToolConfigPersistenceProvider persistenceProvider) {
    this.persistenceProvider =
        Objects.requireNonNull(persistenceProvider, "persistenceProvider must not be null");
  }

  @Override
  public Optional<PlatformToolConfig> getToolConfig(String toolId) {
    if (toolId == null) {
      return Optional.empty();
    }
    return persistenceProvider
        .findByToolId(toolId)
        .map(
            dto ->
                new PlatformToolConfig(
                    dto.id(),
                    dto.toolId(),
                    dto.cacheEnabled(),
                    dto.cacheTtlSeconds(),
                    dto.ragEnabled(),
                    dto.chunkerType(),
                    dto.sourceTable()));
  }
}
