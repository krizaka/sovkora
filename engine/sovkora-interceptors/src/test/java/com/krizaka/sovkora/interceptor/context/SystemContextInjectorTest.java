package com.krizaka.sovkora.interceptor.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemContextInjectorTest {

  @Test
  @DisplayName("intercept injects system metadata and trends into PromptContext")
  void injectsSystemMetadata() {
    SystemContextInjector injector = new SystemContextInjector();
    PromptContext context = new PromptContext("query", Map.of());

    PromptContext result = injector.intercept(context);

    assertThat(result).isNotNull();
    assertThat(result.systemMetadata())
        .containsEntry("systemStatus", "OPERATIONAL")
        .containsEntry("activeTools", "searchKnowledge, ttsGenerator, imageGenerator")
        .containsEntry("activeTrends", "AI-agentic-flows, virtual-threads-concurrency");
  }

  @Test
  @DisplayName("[ADR-064] the session travels under the platform's namespace, not a bare key")
  void propagatesTheNamespacedSession() {
    SystemContextInjector injector = new SystemContextInjector();
    PromptContext context =
        new PromptContext(
            "query",
            Map.of(
                com.krizaka.sovkora.core.domain.model.Context.SESSION_ID_KEY,
                "session-64",
                "sessionId",
                "a bare key a user could have written"));

    assertThat(injector.intercept(context).systemMetadata())
        .containsEntry("sessionId", "session-64");
  }
}
