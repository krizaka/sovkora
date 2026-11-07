package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolPermissionEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.ToolPermissionRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads the roles granted to a tool from {@code platform_tool_permissions}, on every call: removing
 * a role takes effect on the next request, with no cache to go stale.
 */
@Component
class JpaToolPermissionProvider implements ToolPermissionProvider {

  private final ToolPermissionRepository repository;

  JpaToolPermissionProvider(ToolPermissionRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Set<String>> requiredRoles(String toolId) {
    Set<String> roles =
        repository.findByToolId(toolId).stream()
            .map(ToolPermissionEntity::getRequiredRole)
            .collect(Collectors.toUnmodifiableSet());
    return roles.isEmpty() ? Optional.empty() : Optional.of(roles);
  }
}
