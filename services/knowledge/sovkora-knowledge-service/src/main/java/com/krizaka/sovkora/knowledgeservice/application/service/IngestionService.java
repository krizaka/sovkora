package com.krizaka.sovkora.knowledgeservice.application.service;

import com.krizaka.sovkora.knowledgeservice.domain.model.Acl;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Async RAG indexing: loads a tool's not-yet-ingested sources into the vector store and marks them
 * ingested. Dormant until an embedding-model-backed {@link VectorStore} bean exists — with none, a
 * request is a logged no-op (honest skip, never a fake success): there is nothing to embed into.
 *
 * <p><b>A passage carries its source's access rule</b> (ADR-091): tenant, owner and one flag per
 * ACL entry are written as metadata with the text, so the store can filter on them before ranking.
 * A passage has a stable id, and indexing a source first removes what it indexed before — changing
 * an ACL and re-indexing replaces the metadata, it never leaves the old grants behind.
 */
@Service
public class IngestionService {

  private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;

  public IngestionService(
      ObjectProvider<VectorStore> vectorStoreProvider, JdbcTemplate jdbcTemplate) {
    this.vectorStore = vectorStoreProvider.getIfAvailable();
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
  }

  /**
   * Ingests a tool's pending RAG sources into the vector store.
   *
   * @param toolId the tool whose sources to index
   */
  public void ingest(String toolId) {
    if (vectorStore == null) {
      logger.info(
          "Vector store unavailable (no embedding model) — skipping RAG ingestion for {}", toolId);
      return;
    }
    if (toolId == null || toolId.isBlank()) {
      return;
    }
    List<Source> pending =
        jdbcTemplate.query(
            "SELECT id, tool_id, content, tenant_id, user_id, acl_principals FROM orazaka_tools_rag_source"
                + " WHERE tool_id = ? AND ingested = FALSE",
            (rs, rowNum) -> source(rs),
            toolId);
    pending.forEach(this::index);
    logger.info("Ingested {} RAG source(s) for tool {}", pending.size(), toolId);
  }

  /**
   * Indexes one source again under its current access rule — after its ACL changed.
   *
   * @param sourceId the source
   */
  public void reindex(long sourceId) {
    if (vectorStore == null) {
      return;
    }
    jdbcTemplate
        .query(
            "SELECT id, tool_id, content, tenant_id, user_id, acl_principals FROM orazaka_tools_rag_source"
                + " WHERE id = ?",
            (rs, rowNum) -> source(rs),
            sourceId)
        .forEach(this::index);
  }

  private void index(Source source) {
    // Replace, never add beside: the previous passages carry the previous grants.
    vectorStore.delete(
        new FilterExpressionBuilder().eq(Acl.SOURCE, Long.toString(source.id())).build());
    vectorStore.add(
        List.of(
            new Document(
                UUID.nameUUIDFromBytes(("source:" + source.id()).getBytes(StandardCharsets.UTF_8))
                    .toString(),
                source.content(),
                Acl.metadata(
                    source.toolId(), source.id(), source.tenant(), source.owner(), source.acl()))));
    jdbcTemplate.update(
        "UPDATE orazaka_tools_rag_source SET ingested = TRUE WHERE id = ?", source.id());
  }

  private static Source source(java.sql.ResultSet rs) throws java.sql.SQLException {
    Array acl = rs.getArray("acl_principals");
    List<String> entries = acl == null ? List.of() : List.of((String[]) acl.getArray());
    return new Source(
        rs.getLong("id"),
        rs.getString("tool_id"),
        rs.getString("content"),
        rs.getString("tenant_id"),
        rs.getString("user_id"),
        entries);
  }

  private record Source(
      long id, String toolId, String content, String tenant, String owner, List<String> acl) {}
}
