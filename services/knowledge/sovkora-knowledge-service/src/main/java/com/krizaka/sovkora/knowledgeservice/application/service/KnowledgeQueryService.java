package com.krizaka.sovkora.knowledgeservice.application.service;

import com.krizaka.sovkora.knowledgeservice.domain.model.Acl;
import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledgeservice.domain.port.AccessAudit;
import com.krizaka.sovkora.knowledgeservice.domain.port.AccessAudit.Reason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The knowledge service's read surface: semantic {@link #retrieveContext} over the vector store and
 * substring {@link #searchSources} over the tool RAG-source table. Both degrade to an empty string
 * when nothing is available — an unreachable vector store or no matching rows must never fail the
 * caller (RAG enrichment and tool lookup are additive).
 *
 * <p><b>Both are made for a principal, and both filter inside the query</b> (ADR-091): the tenant
 * and the ACL are part of the SQL {@code WHERE} and of the vector search's filter, so a passage the
 * caller may not read is never fetched — not ranked, not counted, not put in a prompt or a cache.
 * Neither method has an overload without a principal, and a caller that acts for nobody is refused
 * by the controller before it gets here.
 *
 * <p>What the caller was refused is recorded by {@link AccessAudit}: the ids of the matching
 * resources of its own tenant that its grants do not cover, never their content.
 *
 * <p>Vector retrieval is dormant until an embedding-model-backed {@link VectorStore} bean exists;
 * {@link #retrieveContext} then returns {@code ""}. Source search is the live path today.
 */
@Service
public class KnowledgeQueryService {

  /** The most refusals one request writes to the audit: a probe, not an inventory. */
  private static final int AUDIT_LIMIT = 20;

  private static final String READABLE =
      "(tenant_id = ? OR tenant_id = '*') AND (COALESCE(user_id = ?, FALSE)"
          + " OR acl_principals && string_to_array(?, ','))";

  private static final String MATCHES = "(? = '' OR LOWER(content) LIKE LOWER('%' || ? || '%'))";

  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;
  private final AccessAudit audit;

  public KnowledgeQueryService(
      ObjectProvider<VectorStore> vectorStoreProvider,
      JdbcTemplate jdbcTemplate,
      AccessAudit audit) {
    this.vectorStore = vectorStoreProvider.getIfAvailable();
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.audit = Objects.requireNonNull(audit, "AccessAudit cannot be null");
  }

  /**
   * Retrieves the relevant context for a query among the passages the principal may read, or {@code
   * ""} when unavailable.
   */
  public String retrieveContext(ActingPrincipal principal, String query, int topK) {
    Objects.requireNonNull(principal, "a retrieval is made for a principal");
    if (vectorStore == null || query == null || query.isBlank()) {
      return "";
    }
    List<Document> documents =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(VectorAccess.readable(principal))
                .build());
    auditUnreadablePassages(principal, query);
    return documents.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
  }

  /**
   * Substring-searches the tool RAG sources the principal may read — its tenant's (or the
   * platform's), owned by it or granted to one of its roles, groups or itself. A blank query
   * returns every readable source for the tool.
   *
   * @return matching sources' content joined by blank lines, or {@code ""} when none match
   */
  public String searchSources(ActingPrincipal principal, String toolId, String query) {
    Objects.requireNonNull(principal, "a search is made for a principal");
    if (toolId == null || toolId.isBlank()) {
      return "";
    }
    String text = query == null ? "" : query;
    String grants = String.join(",", principal.grantees());
    List<String> contents =
        jdbcTemplate.query(
            "SELECT content FROM orazaka_tools_rag_source WHERE tool_id = ? AND "
                + READABLE
                + " AND "
                + MATCHES
                + " ORDER BY id",
            (rs, rowNum) -> rs.getString("content"),
            toolId,
            principal.tenant(),
            principal.subject(),
            grants,
            text,
            text);
    List<Long> refused =
        jdbcTemplate.query(
            "SELECT id FROM orazaka_tools_rag_source WHERE tool_id = ? AND (tenant_id = ? OR tenant_id = '*')"
                + " AND NOT (COALESCE(user_id = ?, FALSE) OR acl_principals && string_to_array(?, ','))"
                + " AND "
                + MATCHES
                + " ORDER BY id LIMIT "
                + AUDIT_LIMIT,
            (rs, rowNum) -> rs.getLong("id"),
            toolId,
            principal.tenant(),
            principal.subject(),
            grants,
            text,
            text);
    refused.forEach(
        id ->
            audit.denied(
                Optional.of(principal), "search", "source", Long.toString(id), Reason.ACL));
    return String.join("\n\n", contents);
  }

  private void auditUnreadablePassages(ActingPrincipal principal, String query) {
    vectorStore
        .similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(AUDIT_LIMIT)
                .similarityThreshold(0.0)
                .filterExpression(VectorAccess.unreadable(principal))
                .build())
        .forEach(
            refused ->
                audit.denied(
                    Optional.of(principal),
                    "retrieve",
                    "passage",
                    String.valueOf(refused.getMetadata().get(Acl.SOURCE)),
                    Reason.ACL));
  }
}
