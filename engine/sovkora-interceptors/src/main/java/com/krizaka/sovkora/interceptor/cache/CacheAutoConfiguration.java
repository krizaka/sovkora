package com.krizaka.sovkora.interceptor.cache;

import com.krizaka.sovkora.core.domain.ports.outbound.EmbeddingClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the semantic cache where its parts exist: a database for the store and the engine's
 * embedding client for the vectors.
 *
 * <p>Registering the bean does not switch the cache on. It stays off twice over: the {@code
 * pipeline_interceptor_config} row ships {@code is_enabled = FALSE}, and a turn is only cached when
 * its pack or route declares {@value SemanticCacheInterceptor#MODE_KEY} (never the default). The
 * order of the chain is that row's {@code execution_order}, never an {@code @Order}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
public class CacheAutoConfiguration {

  /** The cosine threshold when the host sets none: stricter than the floor of 0.94. */
  static final String DEFAULT_THRESHOLD = "0.96";

  /** Answers in PostgreSQL (pgvector), shared by every node of the host. */
  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  @ConditionalOnMissingBean(SemanticCacheStore.class)
  @ConditionalOnProperty(
      prefix = "orazaka.pipeline.cache",
      name = "store",
      havingValue = "pgvector",
      matchIfMissing = true)
  SemanticCacheStore pgVectorSemanticCacheStore(JdbcTemplate jdbcTemplate) {
    return new PgVectorSemanticCacheStore(jdbcTemplate);
  }

  /** One node, no database: a bounded in-memory store, only when the host asks for it. */
  @Bean
  @ConditionalOnMissingBean(SemanticCacheStore.class)
  @ConditionalOnProperty(prefix = "orazaka.pipeline.cache", name = "store", havingValue = "memory")
  SemanticCacheStore inMemorySemanticCacheStore() {
    return new InMemorySemanticCacheStore(5_000);
  }

  /** The vectors come from the catalogue's default embedding model. */
  @Bean
  @ConditionalOnBean(EmbeddingClient.class)
  @ConditionalOnMissingBean(TextEmbedder.class)
  TextEmbedder cacheTextEmbedder(EmbeddingClient embeddingClient) {
    return new EmbeddingClientTextEmbedder(embeddingClient);
  }

  @Bean
  @ConditionalOnBean({SemanticCacheStore.class, TextEmbedder.class})
  SemanticCacheInterceptor semanticCacheInterceptor(
      SemanticCacheStore store,
      TextEmbedder embedder,
      ObjectProvider<Clock> clock,
      @Value("${orazaka.pipeline.cache.threshold:" + DEFAULT_THRESHOLD + "}") double threshold) {
    return new SemanticCacheInterceptor(
        store, embedder, clock.getIfAvailable(Clock::systemUTC), threshold);
  }
}
