package com.krizaka.sovkora.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.routing.SemanticRoutingEngine;
import com.krizaka.sovkora.core.domain.model.*;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamicPipelineExecutorTest {

  @Mock private PipelineRegistry pipelineRegistry;
  @Mock private SemanticRoutingEngine routingEngine;

  private MockInterceptor1 mockInterceptor1;
  private MockInterceptor2 mockInterceptor2;

  private SecurityProperties securityProperties;
  private CoreProperties coreProperties;
  private MeterRegistry meterRegistry;
  private DynamicPipelineExecutor executor;

  private static class MockInterceptor1 implements PromptContextInterceptor {
    private boolean intercepted;

    @Override
    public PromptContext beforeExecution(PromptContext context) {
      intercepted = true;
      return context;
    }

    @Override
    public boolean isAiDependent() {
      return false;
    }
  }

  private static class MockInterceptor2 implements PromptContextInterceptor {
    private boolean intercepted;

    @Override
    public PromptContext beforeExecution(PromptContext context) {
      intercepted = true;
      return context;
    }

    @Override
    public boolean isAiDependent() {
      return true;
    }
  }

  /** A gate: refuses the request deliberately. */
  private static class RefusingInterceptor implements PromptContextInterceptor {
    @Override
    public PromptContext beforeExecution(PromptContext context) {
      throw new PipelineShortCircuitException(
          "RefusingInterceptor", "capability_not_in_plan", "Plan free excludes chat", null);
    }
  }

  /** A cache: answers the turn itself (not a refusal), so nothing after it should run. */
  private static class AnsweringInterceptor implements PromptContextInterceptor {
    @Override
    public PromptContext beforeExecution(PromptContext context) {
      return context.withCachedResponse(new CachedResponse("cached", "EXACT", 1.0));
    }
  }

  /** An enrichment step that breaks: must degrade the answer, never lose the turn. */
  private static class BrokenInterceptor implements PromptContextInterceptor {
    @Override
    public PromptContext beforeExecution(PromptContext context) {
      throw new IllegalStateException("vector store unreachable");
    }
  }

  /** A control that breaks: must stop the turn, never pass it as though it had found nothing. */
  private static class BrokenControl implements PromptContextInterceptor {
    @Override
    public PromptContext beforeExecution(PromptContext context) {
      throw new IllegalStateException("malformed declaration");
    }

    @Override
    public boolean failsClosed() {
      return true;
    }
  }

  @BeforeEach
  void setUp() {
    mockInterceptor1 = new MockInterceptor1();
    mockInterceptor2 = new MockInterceptor2();
    securityProperties = new SecurityProperties(false);
    coreProperties =
        new CoreProperties(
            "ollama",
            new CoreProperties.RagConfig(false, "pgvector", 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                true,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(
                    RoutingMode.DETERMINISTIC, "http://localhost:8085/v1/classify")),
            null,
            null,
            null,
            null);
    meterRegistry = new SimpleMeterRegistry();
  }

  private void initExecutor(List<PromptContextInterceptor> interceptors) {
    executor =
        new DynamicPipelineExecutor(
            interceptors,
            pipelineRegistry,
            routingEngine,
            securityProperties,
            coreProperties,
            meterRegistry,
            event -> {});
  }

  @Nested
  @DisplayName("process()")
  class Process {

    @Test
    @DisplayName(
        "[ADR-062] refuses the turn when orchestration is disabled — it never bypasses the chain")
    void refusesWhenDisabled() {
      coreProperties =
          new CoreProperties(
              "ollama",
              new CoreProperties.RagConfig(false, "pgvector", 3),
              new CoreProperties.McpConfig(List.of()),
              new CoreProperties.OrchestrationConfig(
                  false,
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.RoutingConfig(
                      RoutingMode.DETERMINISTIC, "http://localhost:8085/v1/classify")),
              null,
              null,
              null,
              null);
      initExecutor(List.of(mockInterceptor1));

      var context = Context.anonymous();

      // This test used to assert a null result, and the engine then called the model with the raw
      // prompt — Phase 1's crisis and scope guards, Phase 2's credit hold and the disable-ai gate
      // all
      // skipped. It passed on a hand-built object while the switch could not even be bound
      // (ADR-062).
      assertThrows(PipelineDisabledException.class, () -> executor.process("test", 0, context));
      assertFalse(mockInterceptor1.intercepted, "no interceptor may run for a refused turn");
    }

    @Test
    @DisplayName("executes core and dynamic chains successfully in deterministic mode")
    void executesChainsDeterministic() {
      initExecutor(List.of(mockInterceptor1, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "Desc 1");
      var dynamicConfig = new InterceptorConfig("MockInterceptor2", "Mock 2", 2, true, "Desc 2");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var context = Context.anonymous();
      var result = executor.process("hello", 0, context, "default");

      assertNotNull(result);
      assertEquals("hello", result.refinedPrompt());
      assertTrue(mockInterceptor1.intercepted);
      assertTrue(mockInterceptor2.intercepted);
    }

    @Test
    @DisplayName("[ADR-063] every interceptor sees the media the engine declared, first to last")
    void theDeclaredMediaReachesEveryInterceptor() {
      List<Integer> seen = new java.util.ArrayList<>();
      PromptContextInterceptor first =
          new PromptContextInterceptor() {
            @Override
            public PromptContext beforeExecution(PromptContext context) {
              seen.add(context.mediaParts());
              // An enrichment that hands back a fresh system map, which the API permits: the media
              // declaration is a component, so it cannot be lost with the map.
              return context.withSystemMetadata(java.util.Map.of("replaced", true));
            }
          };
      PromptContextInterceptor second =
          new PromptContextInterceptor() {
            @Override
            public PromptContext beforeExecution(PromptContext context) {
              seen.add(context.mediaParts());
              return context;
            }
          };
      initExecutor(List.of(first, second));
      String firstKey = first.getClass().getSimpleName();
      String secondKey = second.getClass().getSimpleName();
      var config =
          new PipelineConfig(
              "default",
              List.of(new InterceptorConfig(firstKey, "first", 1, true, "")),
              List.of(new InterceptorConfig(secondKey, "second", 2, true, "")),
              List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var result = executor.process("what do you think?", 1, Context.anonymous(), "default");

      assertEquals(List.of(1, 1), seen);
      assertTrue(result.carriesMedia());
    }

    @Test
    @DisplayName(
        "[ADR-064] a caller's preferences cannot name the actor, the conversation or roles")
    void preferencesCannotNameTheEnginesOwnKeys() {
      java.util.concurrent.atomic.AtomicReference<Map<String, Object>> seen =
          new java.util.concurrent.atomic.AtomicReference<>();
      PromptContextInterceptor witness =
          new PromptContextInterceptor() {
            @Override
            public PromptContext beforeExecution(PromptContext context) {
              seen.set(context.userMetadata());
              return context;
            }
          };
      initExecutor(List.of(witness));
      String key = witness.getClass().getSimpleName();
      when(pipelineRegistry.getConfig(any()))
          .thenReturn(
              new PipelineConfig(
                  "default",
                  List.of(new InterceptorConfig(key, "w", 1, true, "")),
                  List.of(),
                  List.of()));
      // Everything a stored preference could say, including the three keys the engine writes
      // itself, one of them below the merge and two above it.
      Map<String, Object> hostile = new java.util.HashMap<>();
      hostile.put("userId", "victim");
      hostile.put("conversationId", "someone-elses-conversation");
      hostile.put("roles", List.of("ROLE_ADMIN"));
      hostile.put("tenantId", "another-tenant");
      hostile.put("orazaka.metering.deferred", true);
      hostile.put("preference.language", "fr");
      Context context =
          new Context(
              "actor", "conversation", hostile, java.util.Set.of(new Authority("ROLE_USER")));

      executor.process("hello", 0, context, "default");

      Map<String, Object> metadata = seen.get();
      assertEquals("actor", metadata.get("userId"), "the actor");
      assertEquals("conversation", metadata.get("conversationId"), "the conversation");
      assertEquals(List.of("ROLE_USER"), metadata.get("roles"), "the roles");
      assertFalse(metadata.containsKey("tenantId"), "an unnamespaced key crossed");
      // The two namespaces do cross: the platform's declaration and the user's own preference.
      assertEquals(true, metadata.get("orazaka.metering.deferred"));
      assertEquals("fr", metadata.get("preference.language"));
    }

    @Test
    @DisplayName("executes core and semantic chains in semantic mode")
    void executesChainsSemantic() {
      coreProperties =
          new CoreProperties(
              "ollama",
              new CoreProperties.RagConfig(false, "pgvector", 3),
              new CoreProperties.McpConfig(List.of()),
              new CoreProperties.OrchestrationConfig(
                  true,
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.RoutingConfig(
                      RoutingMode.SEMANTIC, "http://localhost:8085/v1/classify")),
              null,
              null,
              null,
              null);
      initExecutor(List.of(mockInterceptor1, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "Desc 1");
      var config = new PipelineConfig("default", List.of(coreConfig), List.of(), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);
      when(routingEngine.resolveInterceptors(anyString(), any(), anyMap()))
          .thenReturn(List.of(mockInterceptor2));

      var context = Context.anonymous();
      var result = executor.process("hello", 0, context, "default");

      assertNotNull(result);
      assertTrue(mockInterceptor1.intercepted);
      assertTrue(mockInterceptor2.intercepted);
    }

    @Test
    @DisplayName(
        "throws SecurityException when AI-dependent interceptor run while disableAi is true")
    void throwsSecurityExceptionWhenAiDisabled() {
      // Enable security kill-switch
      securityProperties = new SecurityProperties(true);
      initExecutor(List.of(mockInterceptor1, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "Desc 1");
      var dynamicConfig = new InterceptorConfig("MockInterceptor2", "Mock 2", 2, true, "Desc 2");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var context = Context.anonymous();
      assertThrows(SecurityException.class, () -> executor.process("hello", 0, context, "default"));
    }

    @Test
    @DisplayName("a gate's refusal propagates — a short-circuit must stop the request")
    void propagatesAShortCircuitFromTheCoreChain() {
      var refusing = new RefusingInterceptor();
      initExecutor(List.of(refusing, mockInterceptor1));

      var coreConfig = new InterceptorConfig("RefusingInterceptor", "Gate", 1, true, "");
      var dynamicConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 2, true, "");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var context = Context.anonymous();
      var refusal =
          assertThrows(
              PipelineShortCircuitException.class,
              () -> executor.process("hello", 0, context, "default"));

      assertEquals("capability_not_in_plan", refusal.reason());
      assertFalse(mockInterceptor1.intercepted, "nothing downstream of a refusal may run");
    }

    @Test
    @DisplayName("a refusal in the dynamic chain propagates too")
    void propagatesAShortCircuitFromTheDynamicChain() {
      var refusing = new RefusingInterceptor();
      initExecutor(List.of(mockInterceptor1, refusing));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "");
      var dynamicConfig = new InterceptorConfig("RefusingInterceptor", "Gate", 2, true, "");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var context = Context.anonymous();
      assertThrows(
          PipelineShortCircuitException.class,
          () -> executor.process("hello", 0, context, "default"));
    }

    @Test
    @DisplayName(
        "a cached answer ends the dynamic chain: nothing after it runs, nothing before is skipped")
    void aCachedAnswerStopsTheDynamicChain() {
      var answering = new AnsweringInterceptor();
      initExecutor(List.of(mockInterceptor1, answering, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "");
      var cache = new InterceptorConfig("AnsweringInterceptor", "Cache", 2, true, "");
      var after = new InterceptorConfig("MockInterceptor2", "Mock 2", 3, true, "");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(cache, after), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var result = executor.process("hello", 0, Context.anonymous(), "default");

      assertTrue(mockInterceptor1.intercepted, "the core chain ran in full");
      assertFalse(mockInterceptor2.intercepted, "nothing runs after the answer");
      assertEquals("cached", result.cachedResponse().orElseThrow().text());
    }

    @Test
    @DisplayName("an ordinary interceptor failure is still swallowed — enrichment degrades")
    void swallowsAnOrdinaryFailure() {
      var broken = new BrokenInterceptor();
      initExecutor(List.of(broken, mockInterceptor1));

      var coreConfig = new InterceptorConfig("BrokenInterceptor", "Broken", 1, true, "");
      var dynamicConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 2, true, "");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var context = Context.anonymous();
      var result = executor.process("hello", 0, context, "default");

      assertNotNull(result);
      assertTrue(mockInterceptor1.intercepted, "a broken step must not lose the user's turn");
    }

    @Test
    @DisplayName("[ADR-064] a control's unanticipated failure stops the turn — it declared so")
    void aControlsFailureStopsTheTurn() {
      var broken = new BrokenControl();
      initExecutor(List.of(broken));
      when(pipelineRegistry.getConfig(any()))
          .thenReturn(
              new PipelineConfig(
                  "default",
                  List.of(new InterceptorConfig("BrokenControl", "Broken", 1, true, "")),
                  List.of(),
                  List.of()));

      assertThrows(
          IllegalStateException.class, () -> executor.process("hello", 0, Context.anonymous()));
    }
  }

  @Nested
  @DisplayName("buildSchema()")
  class BuildSchema {

    @Test
    @DisplayName("correctly builds AdvancedPipelineSchema in deterministic mode")
    void buildsSchemaDeterministic() {
      initExecutor(List.of(mockInterceptor1, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "Desc 1");
      var dynamicConfig = new InterceptorConfig("MockInterceptor2", "Mock 2", 2, true, "Desc 2");
      var config =
          new PipelineConfig("default", List.of(coreConfig), List.of(dynamicConfig), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var schema = executor.buildSchema("default", "test query");

      assertEquals("default", schema.pipelineId());
      assertEquals(List.of("MockInterceptor1"), schema.coreInterceptorIds());
      assertEquals(List.of("MockInterceptor2"), schema.dynamicInterceptorIds());
      assertEquals(10L, schema.estimatedLatencyMs());
    }

    @Test
    @DisplayName("correctly builds AdvancedPipelineSchema in semantic mode")
    void buildsSchemaSemantic() {
      coreProperties =
          new CoreProperties(
              "ollama",
              new CoreProperties.RagConfig(false, "pgvector", 3),
              new CoreProperties.McpConfig(List.of()),
              new CoreProperties.OrchestrationConfig(
                  true,
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.InterceptorConfig(null, null, 0.0),
                  new CoreProperties.RoutingConfig(
                      RoutingMode.SEMANTIC, "http://localhost:8085/v1/classify")),
              null,
              null,
              null,
              null);
      initExecutor(List.of(mockInterceptor1, mockInterceptor2));

      var coreConfig = new InterceptorConfig("MockInterceptor1", "Mock 1", 1, true, "Desc 1");
      var config = new PipelineConfig("default", List.of(coreConfig), List.of(), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);
      when(routingEngine.resolveInterceptors(anyString(), any(), anyMap()))
          .thenReturn(List.of(mockInterceptor2));

      var schema = executor.buildSchema("default", "test query");

      assertEquals("default", schema.pipelineId());
      assertEquals(List.of("MockInterceptor1"), schema.coreInterceptorIds());
      assertEquals(List.of("MockInterceptor2"), schema.dynamicInterceptorIds());
    }
  }

  @Nested
  @DisplayName("cacheManagement()")
  class CacheManagement {

    @Test
    @DisplayName("evictAndReload and evictChainCache reload pipelineRegistry")
    void evictsAndReloads() {
      initExecutor(List.of());
      executor.evictAndReload();
      verify(pipelineRegistry, times(1)).reload();

      executor.evictChainCache();
      verify(pipelineRegistry, times(2)).reload();
    }
  }

  @Nested
  @DisplayName("configs()")
  class Configs {

    @Test
    @DisplayName("getCurrentConfig returns combined interceptors")
    void returnsCurrentConfig() {
      initExecutor(List.of());
      var coreConfig = new InterceptorConfig("mockInterceptor", "Mock", 1, true, "Desc");
      var config = new PipelineConfig("default", List.of(coreConfig), List.of(), List.of());
      when(pipelineRegistry.getConfig(any())).thenReturn(config);

      var current = executor.getCurrentConfig();
      assertEquals(1, current.size());
      assertEquals("mockInterceptor", current.getFirst().interceptorKey());
    }

    @Test
    @DisplayName("buildDefaultConfigs humanizes interceptor names")
    void buildsDefaultConfigs() {
      initExecutor(List.of(mockInterceptor1));
      var defaults = executor.buildDefaultConfigs();

      assertEquals(1, defaults.size());
      assertNotNull(defaults.getFirst().displayLabel());
    }
  }
}
