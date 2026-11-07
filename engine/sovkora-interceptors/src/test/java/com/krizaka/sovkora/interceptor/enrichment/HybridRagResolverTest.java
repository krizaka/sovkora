package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HybridRagResolverTest {

  private final HybridRagResolver resolver = new HybridRagResolver();

  private PromptContext createContext(String refinedPrompt, Map<String, Object> userMeta) {
    return new PromptContext("raw", userMeta, Map.of(), refinedPrompt, null, null);
  }

  @Test
  void intercept_passesThrough_stubReturnsEmptyResults() {
    PromptContext ctx = createContext("Find me relevant documents", Map.of());
    PromptContext result = resolver.intercept(ctx);

    // Stub returns empty for both sparse and dense, so no RRF context injected
    assertThat(result.systemMetadata()).doesNotContainKey("rrfContextMatrix");
  }

  @Test
  void intercept_resolvesDefaultTenantId_whenNotSpecified() {
    PromptContext ctx = createContext("test", Map.of());
    PromptContext result = resolver.intercept(ctx);
    // Should not throw and should pass through
    assertThat(result).isNotNull();
  }

  @Test
  void intercept_resolvesCustomTenantId() {
    var userMeta = new HashMap<String, Object>();
    userMeta.put("tenantId", "tenant-42");
    PromptContext ctx = createContext("test query", Map.copyOf(userMeta));
    PromptContext result = resolver.intercept(ctx);
    assertThat(result).isNotNull();
  }

  @Test
  void intercept_handlesBlankTenantId() {
    var userMeta = new HashMap<String, Object>();
    userMeta.put("tenantId", "   ");
    PromptContext ctx = createContext("test", Map.copyOf(userMeta));
    PromptContext result = resolver.intercept(ctx);
    assertThat(result).isNotNull();
  }

  @Test
  void intercept_handlesNullRefinedPrompt() {
    PromptContext ctx = createContext(null, Map.of());
    PromptContext result = resolver.intercept(ctx);
    assertThat(result).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "MOCK_SPARSE_RETRIEVAL test | [BM25] mock sparse context content",
        "MOCK_DENSE_RETRIEVAL test | [PGVector] mock dense context content",
        "MOCK_SPARSE_RETRIEVAL and MOCK_DENSE_RETRIEVAL test | [BM25] mock sparse context content [PGVector] mock dense context content"
      },
      delimiter = '|')
  void intercept_injectsFusedContext(String prompt, String expectedMatrix) {
    PromptContext ctx = createContext(prompt, Map.of());
    PromptContext result = resolver.intercept(ctx);

    assertThat(result.systemMetadata()).containsEntry("rrfContextMatrix", expectedMatrix);
  }
}
