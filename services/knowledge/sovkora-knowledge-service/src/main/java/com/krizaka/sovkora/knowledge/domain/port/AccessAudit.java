package com.krizaka.sovkora.knowledge.domain.port;

import com.krizaka.sovkora.knowledge.domain.model.ActingPrincipal;
import java.util.Optional;

/**
 * The record of refused reads (ADR-091). A line names who asked, which resource was refused and why
 * — never the query and never the content (ADR-064).
 */
public interface AccessAudit {

  /** Why a read was refused. */
  enum Reason {
    NO_PRINCIPAL("no-principal"),
    ACL("acl"),
    NOT_ADMIN("not-admin"),
    FOREIGN_TENANT("foreign-tenant");

    private final String code;

    Reason(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  /**
   * Records a refusal.
   *
   * @param principal who asked, or empty when the call acted for nobody
   * @param action what was attempted, e.g. {@code search}
   * @param resourceKind {@code source}, {@code passage} or {@code request}
   * @param resourceId the refused resource's id
   * @param reason why
   */
  void denied(
      Optional<ActingPrincipal> principal,
      String action,
      String resourceKind,
      String resourceId,
      Reason reason);
}
