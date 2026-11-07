package com.krizaka.sovkora.knowledge.infrastructure.adapter.persistence;

import com.krizaka.sovkora.knowledge.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledge.domain.port.AccessAudit;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes the refusals to {@code knowledge_access_audit}. */
@Component
class JdbcAccessAuditAdapter implements AccessAudit {

  private final JdbcTemplate jdbcTemplate;

  JdbcAccessAuditAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
  }

  @Override
  public void denied(
      Optional<ActingPrincipal> principal,
      String action,
      String resourceKind,
      String resourceId,
      Reason reason) {
    jdbcTemplate.update(
        "INSERT INTO knowledge_access_audit (tenant_id, subject, action, resource_kind, resource_id, reason)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        principal.map(ActingPrincipal::tenant).orElse(null),
        principal.map(ActingPrincipal::subject).orElse(null),
        action,
        resourceKind,
        resourceId,
        reason.code());
  }
}
