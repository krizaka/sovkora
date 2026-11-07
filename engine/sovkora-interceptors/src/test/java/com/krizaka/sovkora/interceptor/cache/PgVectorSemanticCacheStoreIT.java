package com.krizaka.sovkora.interceptor.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

/**
 * The pgvector store against a real PostgreSQL, with the shipped DDL ({@code
 * db/semantic-cache.sql}): the partition is in every {@code WHERE}, expiry is honoured before any
 * sweep, and the nearest entry is the one a cosine ranking says.
 */
@SpringBootTest(classes = PgVectorSemanticCacheStoreIT.Cfg.class)
@DisplayName("PgVectorSemanticCacheStore — real pgvector")
class PgVectorSemanticCacheStoreIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
  static class Cfg {}

  private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  private static final Instant LATER = NOW.plus(Duration.ofDays(1));

  @Autowired private JdbcTemplate jdbc;

  private PgVectorSemanticCacheStore store;

  @BeforeAll
  static void noop() {}

  private PgVectorSemanticCacheStore store() throws Exception {
    if (store == null) {
      jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
      jdbc.execute(
          StreamUtils.copyToString(
              new ClassPathResource("db/semantic-cache.sql").getInputStream(),
              StandardCharsets.UTF_8));
      jdbc.execute("TRUNCATE semantic_cache_entry");
      store = new PgVectorSemanticCacheStore(jdbc);
    }
    return store;
  }

  private static float[] vector(int hot) {
    float[] v = new float[PgVectorSemanticCacheStore.DIMENSIONS];
    v[hot] = 1f;
    return v;
  }

  private static CachePartition acme() {
    return new CachePartition("acme", "g".repeat(64), "default");
  }

  @Test
  @DisplayName("an answer is found by exact key, and by the nearest embedding with its signature")
  void exactAndNearest() throws Exception {
    PgVectorSemanticCacheStore s = store();
    jdbc.execute("TRUNCATE semantic_cache_entry");
    s.put(acme(), "k".repeat(64), vector(3), "n:100", "hundred", LATER);
    s.put(acme(), "j".repeat(64), vector(9), "n:5", "five", LATER);

    assertThat(s.findExact(acme(), "k".repeat(64), NOW)).contains("hundred");

    List<SemanticCacheStore.Candidate> near = s.nearest(acme(), vector(3), 2, NOW);
    assertThat(near).hasSize(2);
    assertThat(near.getFirst().response()).isEqualTo("hundred");
    assertThat(near.getFirst().signature()).isEqualTo("n:100");
    assertThat(near.getFirst().similarity()).isGreaterThan(0.999);
    assertThat(near.get(1).similarity()).isLessThan(0.1);
  }

  @Test
  @DisplayName("never across tenants, scope guards or model tiers")
  void partitionIsolation() throws Exception {
    PgVectorSemanticCacheStore s = store();
    jdbc.execute("TRUNCATE semantic_cache_entry");
    s.put(acme(), "k".repeat(64), vector(1), "", "acme answer", LATER);

    CachePartition other = new CachePartition("globex", "g".repeat(64), "default");
    CachePartition otherScope = new CachePartition("acme", "h".repeat(64), "default");
    CachePartition otherTier = new CachePartition("acme", "g".repeat(64), "premium");
    for (CachePartition p : List.of(other, otherScope, otherTier)) {
      assertThat(s.findExact(p, "k".repeat(64), NOW)).isEmpty();
      assertThat(s.nearest(p, vector(1), 3, NOW)).isEmpty();
    }
  }

  @Test
  @DisplayName("an expired entry is never returned, before or after the sweep")
  void expiry() throws Exception {
    PgVectorSemanticCacheStore s = store();
    jdbc.execute("TRUNCATE semantic_cache_entry");
    s.put(acme(), "k".repeat(64), vector(1), "", "old", NOW.plusSeconds(10));
    Instant after = NOW.plusSeconds(11);

    assertThat(s.findExact(acme(), "k".repeat(64), after)).isEmpty();
    assertThat(s.nearest(acme(), vector(1), 3, after)).isEmpty();
    assertThat(s.purgeExpired(after)).isEqualTo(1);
  }

  @Test
  @DisplayName("a tenant can be forgotten without touching the others")
  void invalidateTenant() throws Exception {
    PgVectorSemanticCacheStore s = store();
    jdbc.execute("TRUNCATE semantic_cache_entry");
    s.put(acme(), "k".repeat(64), vector(1), "", "a", LATER);
    s.put(
        new CachePartition("globex", "g".repeat(64), "default"),
        "k".repeat(64),
        vector(1),
        "",
        "g",
        LATER);

    assertThat(s.invalidateTenant("acme")).isEqualTo(1);
    assertThat(s.findExact(acme(), "k".repeat(64), NOW)).isEmpty();
    assertThat(
            s.findExact(
                new CachePartition("globex", "g".repeat(64), "default"), "k".repeat(64), NOW))
        .contains("g");
  }

  @Test
  @DisplayName(
      "an exact-only entry (no embedding) never comes back as a nearest one; a replay overwrites")
  void exactOnlyAndUpsert() throws Exception {
    PgVectorSemanticCacheStore s = store();
    jdbc.execute("TRUNCATE semantic_cache_entry");
    s.put(acme(), "k".repeat(64), new float[0], "", "first", LATER);
    assertThat(s.nearest(acme(), vector(1), 3, NOW)).isEmpty();
    s.put(acme(), "k".repeat(64), new float[0], "", "second", LATER);
    assertThat(s.findExact(acme(), "k".repeat(64), NOW)).contains("second");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM semantic_cache_entry", Long.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("a vector of another size is refused, not stored truncated")
  void wrongDimension() throws Exception {
    PgVectorSemanticCacheStore s = store();
    float[] small = Arrays.copyOf(vector(0), 8);
    assertThatThrownBy(() -> s.put(acme(), "k".repeat(64), small, "", "x", LATER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> s.nearest(acme(), small, 1, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
