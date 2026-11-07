package com.krizaka.sovkora.interceptor.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
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
 * The semantic cache LIVE: a real embedding model (bge-m3 through Ollama, {@code
 * ORAZAKA_LIVE_OLLAMA_URL}) and a real pgvector. A repeated question hits; a number or a negation
 * changed does not, although the cosine says "same". Skipped when no Ollama is configured.
 */
@SpringBootTest(classes = SemanticCacheLiveIT.Cfg.class)
class SemanticCacheLiveIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
  static class Cfg {}

  @Autowired private JdbcTemplate jdbc;

  private static float[] embed(String url, String text) throws Exception {
    String body = "{\"model\":\"bge-m3\",\"input\":" + quote(text) + "}";
    HttpResponse<String> r =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(url + "/api/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    String[] nums = r.body().split("\\[\\[")[1].split("\\]\\]")[0].split(",");
    float[] v = new float[nums.length];
    for (int i = 0; i < nums.length; i++) {
      v[i] = Float.parseFloat(nums[i]);
    }
    return v;
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static PromptContext turn(String q) {
    Map<String, Object> meta = new HashMap<>();
    meta.put("orazaka.tenantId", "live");
    meta.put(SemanticCacheInterceptor.MODE_KEY, "semantic");
    return new PromptContext(q, meta);
  }

  @Test
  @DisplayName("live: a repeat hits; a changed number or negation never does")
  void live() throws Exception {
    String url = System.getenv("ORAZAKA_LIVE_OLLAMA_URL");
    assumeTrue(url != null && !url.isBlank(), "no ORAZAKA_LIVE_OLLAMA_URL");
    jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
    jdbc.execute(
        StreamUtils.copyToString(
            new ClassPathResource("db/semantic-cache.sql").getInputStream(),
            java.nio.charset.StandardCharsets.UTF_8));
    jdbc.execute("TRUNCATE semantic_cache_entry");
    var cache =
        new SemanticCacheInterceptor(
            new PgVectorSemanticCacheStore(jdbc),
            t -> {
              try {
                return embed(url, t);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            Clock.systemUTC(),
            0.96);
    for (String q :
        new String[] {
          "What is the notice period to terminate my lease?",
          "I can cancel my order",
          "Is it allowed to sublet the apartment?",
          "Refund order 100 please"
        }) {
      cache.responseServed(turn(q), "answer to " + q);
    }
    // repeats and a near-identical wording: HIT
    assertThat(
            cache
                .beforeExecution(turn("What is the notice period to terminate my lease?"))
                .cachedResponse())
        .isPresent();
    assertThat(
            cache
                .beforeExecution(turn("what's the notice period for terminating my lease"))
                .cachedResponse())
        .isPresent();
    // negation (cosine 0.961 >= threshold) and number changes: NO hit
    assertThat(cache.beforeExecution(turn("I cannot cancel my order")).cachedResponse()).isEmpty();
    assertThat(
            cache
                .beforeExecution(turn("Is it not allowed to sublet the apartment?"))
                .cachedResponse())
        .isEmpty();
    assertThat(cache.beforeExecution(turn("Refund order 1000 please")).cachedResponse()).isEmpty();
  }
}
