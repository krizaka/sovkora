package com.krizaka.sovkora.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.core.domain.model.PipelineConfig;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRegistryTest {

  @Mock private PipelineConfigProvider configProvider;

  private PipelineRegistry registry;

  @BeforeEach
  void setUp() {
    // Return empty list by default to let initial load run quietly
    when(configProvider.findAllOrdered()).thenReturn(List.of());
    registry = new PipelineRegistry(configProvider);
  }

  @Test
  void getConfig_nonExistentPipelineId_returnsDefaultConfig() {
    PipelineConfig config = registry.getConfig("non-existent");
    assertNotNull(config);
    assertEquals(PipelineConfig.DEFAULT_PIPELINE_ID, config.pipelineId());
    assertFalse(config.coreInterceptorKeys().isEmpty());
    assertTrue(config.dynamicInterceptorKeys().isEmpty());
  }

  @Test
  void getConfig_nullPipelineId_returnsDefaultConfig() {
    PipelineConfig config = registry.getConfig(null);
    assertNotNull(config);
    assertEquals(PipelineConfig.DEFAULT_PIPELINE_ID, config.pipelineId());
  }

  @Test
  void getActiveInterceptorIds_returnsCoreAndDynamicInterceptors() {
    InterceptorConfig coreConfig =
        new InterceptorConfig(
            "UserContextResolver", "User Context Resolver", 0, true, "Core interceptor");
    InterceptorConfig dynamicConfig =
        new InterceptorConfig(
            "RefinerInterceptor", "Refiner Interceptor", 3, true, "Dynamic interceptor");

    when(configProvider.findAllOrdered()).thenReturn(List.of(coreConfig, dynamicConfig));
    registry.reload();

    List<String> activeIds = registry.getActiveInterceptorIds(PipelineConfig.DEFAULT_PIPELINE_ID);
    assertNotNull(activeIds);
    assertTrue(activeIds.contains("UserContextResolver"));
    assertTrue(activeIds.contains("RefinerInterceptor"));
  }

  @Test
  void reload_providerThrowsException_retainsPreviousSnapshot() {
    InterceptorConfig coreConfig =
        new InterceptorConfig(
            "UserContextResolver", "User Context Resolver", 0, true, "Core interceptor");
    when(configProvider.findAllOrdered()).thenReturn(List.of(coreConfig));
    registry.reload();

    PipelineConfig originalConfig = registry.getConfig(PipelineConfig.DEFAULT_PIPELINE_ID);

    // Make provider throw exception on next reload
    when(configProvider.findAllOrdered()).thenThrow(new RuntimeException("DB error"));
    registry.reload();

    PipelineConfig postErrorConfig = registry.getConfig(PipelineConfig.DEFAULT_PIPELINE_ID);
    assertEquals(originalConfig, postErrorConfig);
  }

  @Test
  void reload_providerThrowsExceptionOnEmptyCache_loadsDefaultConfig() {
    PipelineConfigProvider failingProvider = mock(PipelineConfigProvider.class);
    when(failingProvider.findAllOrdered()).thenThrow(new RuntimeException("DB error"));

    PipelineRegistry registryWithError = new PipelineRegistry(failingProvider);
    PipelineConfig config = registryWithError.getConfig(PipelineConfig.DEFAULT_PIPELINE_ID);
    assertNotNull(config);
    assertEquals(PipelineConfig.DEFAULT_PIPELINE_ID, config.pipelineId());
  }

  @Test
  void scopeGuard_isCore_soADisabledRowCannotSwitchOffARegulatoryControl() {
    // An admin row that disables the guard must not silence a SENSITIVE pack's refusal:
    // the scope guard of ADR-051 is a control of the regulatory class, not a preference.
    InterceptorConfig disabled =
        new InterceptorConfig("ScopeGuardInterceptor", "Scope Guard", 0, false, "");
    when(configProvider.findAllOrdered()).thenReturn(List.of(disabled));
    registry.reload();

    PipelineConfig config = registry.getConfig(PipelineConfig.DEFAULT_PIPELINE_ID);
    assertTrue(
        config.coreInterceptorKeys().contains("ScopeGuardInterceptor"),
        "the scope guard must run in the non-bypassable core phase");
    assertTrue(
        config.coreInterceptorKeys().indexOf("ScopeGuardInterceptor") < 2,
        "it must refuse before any enrichment touches the turn");
  }

  @Test
  void scopeGuard_isCore_evenWhenTheDatabaseNeverHeardOfIt() {
    when(configProvider.findAllOrdered()).thenReturn(List.of());
    registry.reload();

    assertTrue(
        registry
            .getConfig(PipelineConfig.DEFAULT_PIPELINE_ID)
            .coreInterceptorKeys()
            .contains("ScopeGuardInterceptor"));
  }

  @Test
  void safety_runsBeforeScope_becauseTheOrderDecidesWhatSomeoneInCrisisReads() {
    // A turn saying both "je ne veux plus vivre" and "quel médicament" matches both guards, and
    // whichever runs first answers it. A scope refusal — "I don't discuss medication" — would be a
    // correct sentence and the wrong one (ADR-055 §4).
    when(configProvider.findAllOrdered()).thenReturn(List.of());
    registry.reload();

    List<String> core =
        registry.getConfig(PipelineConfig.DEFAULT_PIPELINE_ID).coreInterceptorKeys();

    assertEquals("SafetyInterceptor", core.getFirst());
    assertTrue(core.indexOf("SafetyInterceptor") < core.indexOf("ScopeGuardInterceptor"));
  }

  @Test
  void safety_isCore_soNoRowCanSwitchOffACrisisGuard() {
    InterceptorConfig disabled = new InterceptorConfig("SafetyInterceptor", "Safety", 0, false, "");
    when(configProvider.findAllOrdered()).thenReturn(List.of(disabled));
    registry.reload();

    assertTrue(
        registry
            .getConfig(PipelineConfig.DEFAULT_PIPELINE_ID)
            .coreInterceptorKeys()
            .contains("SafetyInterceptor"),
        "resolveCoreChain never consults `enabled`, and that is the whole point here");
  }
}
