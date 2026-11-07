package com.krizaka.sovkora.interceptor.cache;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Keeps cached answers in PostgreSQL with pgvector ({@code db/semantic-cache.sql}).
 *
 * <p>Every statement is scoped to the partition (tenant, scope guard, model tier) in its {@code
 * WHERE}: no read ever sees another partition, and an expired row is never returned even before the
 * sweep removes it. Similarity is {@code 1 - cosine distance}.
 */
public final class PgVectorSemanticCacheStore implements SemanticCacheStore {

  /** The vector size of the table: the embedding model's (bge-m3). */
  public static final int DIMENSIONS = 1024;

  private static final String PARTITION =
      "tenant_id = ? AND scope_guard_hash = ? AND model_tier = ?";

  private final JdbcTemplate jdbc;

  /**
   * @param jdbc the engine host's database, with the {@code semantic_cache_entry} table
   */
  public PgVectorSemanticCacheStore(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
  }

  @Override
  public Optional<String> findExact(CachePartition partition, String exactKey, Instant now) {
    return jdbc
        .query(
            "SELECT response FROM semantic_cache_entry WHERE "
                + PARTITION
                + " AND exact_key = ? AND expires_at > ? LIMIT 1",
            (rs, row) -> rs.getString(1),
            partition.tenantId(),
            partition.scopeGuardHash(),
            partition.modelTier(),
            exactKey,
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  @Override
  public List<Candidate> nearest(CachePartition partition, float[] embedding, int k, Instant now) {
    requireSize(embedding);
    String vector = literal(embedding);
    return jdbc.query(
        "SELECT response, signature, 1 - (embedding <=> ?::vector) AS similarity"
            + " FROM semantic_cache_entry WHERE "
            + PARTITION
            + " AND embedding IS NOT NULL AND expires_at > ?"
            + " ORDER BY embedding <=> ?::vector LIMIT ?",
        (rs, row) -> new Candidate(rs.getString(1), rs.getString(2), rs.getDouble(3)),
        vector,
        partition.tenantId(),
        partition.scopeGuardHash(),
        partition.modelTier(),
        Timestamp.from(now),
        vector,
        k);
  }

  @Override
  public void put(
      CachePartition partition,
      String exactKey,
      float[] embedding,
      String signature,
      String response,
      Instant expiresAt) {
    boolean semantic = embedding != null && embedding.length > 0;
    if (semantic) {
      requireSize(embedding);
    }
    jdbc.update(
        "INSERT INTO semantic_cache_entry (tenant_id, scope_guard_hash, model_tier, exact_key,"
            + " embedding, signature, response, expires_at)"
            + " VALUES (?, ?, ?, ?, ?::vector, ?, ?, ?)"
            + " ON CONFLICT (tenant_id, scope_guard_hash, model_tier, exact_key) DO UPDATE SET"
            + " embedding = EXCLUDED.embedding, signature = EXCLUDED.signature,"
            + " response = EXCLUDED.response, expires_at = EXCLUDED.expires_at, created_at = now()",
        partition.tenantId(),
        partition.scopeGuardHash(),
        partition.modelTier(),
        exactKey,
        semantic ? literal(embedding) : null,
        signature,
        response,
        Timestamp.from(expiresAt));
  }

  @Override
  public int invalidateTenant(String tenantId) {
    return jdbc.update("DELETE FROM semantic_cache_entry WHERE tenant_id = ?", tenantId);
  }

  @Override
  public int purgeExpired(Instant now) {
    return jdbc.update(
        "DELETE FROM semantic_cache_entry WHERE expires_at <= ?", Timestamp.from(now));
  }

  private static void requireSize(float[] embedding) {
    if (embedding == null || embedding.length != DIMENSIONS) {
      throw new IllegalArgumentException(
          "the cache table holds "
              + DIMENSIONS
              + "-dimension vectors, got "
              + (embedding == null ? 0 : embedding.length)
              + ": another embedding model is a migration");
    }
  }

  /** The pgvector text form: {@code [0.1,0.2,…]}. */
  static String literal(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float value : embedding) {
      joiner.add(Float.toString(value));
    }
    return joiner.toString();
  }
}
