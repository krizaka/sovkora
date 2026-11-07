package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;
import java.util.Set;

/**
 * The roles an administrator granted a tool (ADR-091): data in the database, read on every call so
 * a change holds on the next request. Empty means "nothing configured", and the tool's own declared
 * default applies.
 */
public interface ToolPermissionProvider {

  /**
   * The roles, any of which may use the tool.
   *
   * @param toolId the tool's name
   * @return the configured roles, or empty when none are configured
   */
  Optional<Set<String>> requiredRoles(String toolId);
}
