package com.krizaka.sovkora.knowledge.application.service;

import static com.krizaka.sovkora.knowledge.domain.model.Acl.OWNER;
import static com.krizaka.sovkora.knowledge.domain.model.Acl.TENANT;

import com.krizaka.sovkora.knowledge.domain.model.Acl;
import com.krizaka.sovkora.knowledge.domain.model.ActingPrincipal;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * The access rule of {@link Acl}, written as a vector-store filter. It is handed to the store with
 * the similarity search, so the store applies it before ranking: a passage the caller cannot read
 * is never a candidate.
 */
final class VectorAccess {

  private VectorAccess() {}

  /** The passages the principal may read: its tenant's (or the platform's) and granted to it. */
  static Filter.Expression readable(ActingPrincipal principal) {
    FilterExpressionBuilder b = new FilterExpressionBuilder();
    return b.and(tenantClause(b, principal), grantClause(b, principal)).build();
  }

  /**
   * The passages of the principal's tenant it may <em>not</em> read — for the denial audit only.
   */
  static Filter.Expression unreadable(ActingPrincipal principal) {
    FilterExpressionBuilder b = new FilterExpressionBuilder();
    return b.and(tenantClause(b, principal), b.not(grantClause(b, principal))).build();
  }

  private static FilterExpressionBuilder.Op tenantClause(
      FilterExpressionBuilder b, ActingPrincipal principal) {
    return b.or(b.eq(TENANT, principal.tenant()), b.eq(TENANT, ActingPrincipal.PLATFORM_TENANT));
  }

  private static FilterExpressionBuilder.Op grantClause(
      FilterExpressionBuilder b, ActingPrincipal principal) {
    FilterExpressionBuilder.Op clause = b.eq(OWNER, principal.subject());
    for (String entry : principal.grantees()) {
      clause = b.or(clause, b.eq(Acl.key(entry), Boolean.TRUE));
    }
    return clause;
  }
}
