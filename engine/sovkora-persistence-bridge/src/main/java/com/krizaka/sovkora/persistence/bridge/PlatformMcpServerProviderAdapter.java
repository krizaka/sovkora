package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformMcpServerPersistenceProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Implementation of the outbound PlatformMcpServerProvider port using the app persistence provider.
 */
@Service
class PlatformMcpServerProviderAdapter implements PlatformMcpServerProvider {

  private final PlatformMcpServerPersistenceProvider persistenceProvider;

  PlatformMcpServerProviderAdapter(PlatformMcpServerPersistenceProvider persistenceProvider) {
    this.persistenceProvider =
        Objects.requireNonNull(persistenceProvider, "persistenceProvider must not be null");
  }

  @Override
  public List<PlatformMcpServer> getActivePlatformMcpServers() {
    return persistenceProvider.findByEnabledTrue().stream()
        .map(
            dto ->
                new PlatformMcpServer(
                    dto.id(),
                    dto.label(),
                    dto.transportType(),
                    dto.url(),
                    dto.command(),
                    dto.args(),
                    dto.authToken(),
                    dto.enabled()))
        .toList();
  }
}
