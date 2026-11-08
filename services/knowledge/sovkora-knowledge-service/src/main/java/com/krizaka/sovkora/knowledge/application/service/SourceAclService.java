package com.krizaka.sovkora.knowledge.application.service;

import com.krizaka.sovkora.knowledge.domain.model.Acl;
import com.krizaka.sovkora.knowledge.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledge.domain.port.AccessAudit;
import com.krizaka.sovkora.knowledge.domain.port.AccessAudit.Reason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Who may read a source is data an administrator sets (ADR-091): this replaces a source's ACL. Only
 * an administrator of the source's own tenant may, and a source of another tenant (or of the
 * platform) answers exactly as a missing one. The source is indexed again under the new rule in the
 * same call, so the change holds on the next request.
 */
@Service
public class SourceAclService {

  /** The role that may edit the ACLs of its tenant's sources. */
  public static final String ADMIN_ROLE = "ROLE_ADMIN";

  private final JdbcTemplate jdbcTemplate;
  private final IngestionService ingestion;
  private final AccessAudit audit;

  public SourceAclService(
      JdbcTemplate jdbcTemplate, IngestionService ingestion, AccessAudit audit) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.ingestion = Objects.requireNonNull(ingestion, "IngestionService cannot be null");
    this.audit = Objects.requireNonNull(audit, "AccessAudit cannot be null");
  }

  /** What an ACL change did. */
  public enum Outcome {
    CHANGED,
    NOT_FOUND,
    FORBIDDEN,
    INVALID
  }

  /**
   * Replaces the ACL of a source.
   *
   * @param admin who asks
   * @param sourceId the source
   * @param entries the new entries ({@code role:…}, {@code group:…}, {@code user:…}); empty means
   *     the owner alone
   */
  public Outcome replace(ActingPrincipal admin, long sourceId, List<String> entries) {
    if (entries == null || !entries.stream().allMatch(Acl::isEntry)) {
      return Outcome.INVALID;
    }
    if (!admin.hasRole(ADMIN_ROLE)) {
      audit.denied(
          Optional.of(admin), "acl-update", "source", Long.toString(sourceId), Reason.NOT_ADMIN);
      return Outcome.FORBIDDEN;
    }
    int changed =
        jdbcTemplate.update(
            "UPDATE orazaka_tools_rag_source SET acl_principals = COALESCE(string_to_array(NULLIF(?, ''), ','), '{}'),"
                + " ingested = FALSE WHERE id = ? AND tenant_id = ?",
            String.join(",", entries),
            sourceId,
            admin.tenant());
    if (changed == 0) {
      return Outcome.NOT_FOUND;
    }
    ingestion.reindex(sourceId);
    return Outcome.CHANGED;
  }
}
