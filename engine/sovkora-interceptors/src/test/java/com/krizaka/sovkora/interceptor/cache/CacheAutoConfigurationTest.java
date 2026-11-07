package com.krizaka.sovkora.interceptor.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.ports.outbound.EmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("the semantic cache is wired where its parts exist")
class CacheAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

  @Test
  @DisplayName("a database and an embedding client give the pgvector store and the interceptor")
  void wired() {
    runner
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(EmbeddingClient.class, () -> mock(EmbeddingClient.class))
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(SemanticCacheInterceptor.class);
              assertThat(ctx.getBean(SemanticCacheStore.class))
                  .isInstanceOf(PgVectorSemanticCacheStore.class);
              assertThat(ctx.getBean(TextEmbedder.class))
                  .isInstanceOf(EmbeddingClientTextEmbedder.class);
            });
  }

  @Test
  @DisplayName("no embedding client, no interceptor: the pipeline never meets a half-built cache")
  void noEmbedder() {
    runner
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(SemanticCacheInterceptor.class));
  }

  @Test
  @DisplayName("no database and no explicit memory store: nothing is cached anywhere by accident")
  void noDatabase() {
    runner
        .withBean(EmbeddingClient.class, () -> mock(EmbeddingClient.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(SemanticCacheInterceptor.class));
  }

  @Test
  @DisplayName("the memory store only on request")
  void memoryOnRequest() {
    runner
        .withBean(EmbeddingClient.class, () -> mock(EmbeddingClient.class))
        .withPropertyValues("orazaka.pipeline.cache.store=memory")
        .run(
            ctx ->
                assertThat(ctx.getBean(SemanticCacheStore.class))
                    .isInstanceOf(InMemorySemanticCacheStore.class));
  }
}
