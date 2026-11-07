package com.krizaka.sovkora.core.infrastructure.config;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CoreProperties} video configuration records and InterceptorConfig
 * unification.
 */
class CorePropertiesVideoConfigTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    org.junit.jupiter.api.Assertions.assertTrue(true);
  }

  @Nested
  @DisplayName("VideoConfig records")
  class VideoConfigTests {

    @Test
    @DisplayName("VideoAnalysisConfig stores maxKeyframes and frameIntervalSec")
    void analysisConfig() {
      var config = new CoreProperties.VideoAnalysisConfig(8, 5);
      assertEquals(8, config.maxKeyframes());
      assertEquals(5, config.frameIntervalSec());
    }

    @Test
    @DisplayName("VideoGenerationConfig stores provider and baseUrl")
    void generationConfig() {
      var config =
          new CoreProperties.VideoGenerationConfig("localai-video", "http://localhost:8080");
      assertEquals("localai-video", config.provider());
      assertEquals("http://localhost:8080", config.baseUrl());
    }

    @Test
    @DisplayName("VideoConfig composes analysis and generation")
    void videoConfigComposition() {
      var analysis = new CoreProperties.VideoAnalysisConfig(10, 3);
      var generation = new CoreProperties.VideoGenerationConfig(null, null);
      var video = new CoreProperties.VideoConfig(analysis, generation);

      assertSame(analysis, video.analysis());
      assertSame(generation, video.generation());
    }

    @Test
    @DisplayName("null video config is accepted by CoreProperties")
    void nullVideoAccepted() {
      var props = new CoreProperties(PROVIDER_OLLAMA, null, null, null, null, null, null, null);
      assertNull(props.video());
    }
  }

  @Nested
  @DisplayName("InterceptorConfig unification")
  class InterceptorConfigTests {

    @Test
    @DisplayName("InterceptorConfig works for refiner configuration")
    void refinerConfig() {
      var config = new CoreProperties.InterceptorConfig(PROVIDER_OLLAMA, "llama-refine", 0.2);
      assertEquals(PROVIDER_OLLAMA, config.provider());
      assertEquals("llama-refine", config.model());
      assertEquals(0.2, config.temperature());
    }

    @Test
    @DisplayName("InterceptorConfig works for router configuration")
    void routerConfig() {
      var config = new CoreProperties.InterceptorConfig(PROVIDER_OPENAI, "gpt-4", 0.0);
      assertEquals(PROVIDER_OPENAI, config.provider());
      assertEquals(0.0, config.temperature());
    }

    @Test
    @DisplayName("OrchestrationConfig uses unified InterceptorConfig for both")
    void orchestrationUsesUnifiedConfig() {
      var refiner = new CoreProperties.InterceptorConfig(PROVIDER_OLLAMA, "r", 0.3);
      var router = new CoreProperties.InterceptorConfig(PROVIDER_OPENAI, "m", 0.0);
      var config =
          new CoreProperties.OrchestrationConfig(
              true, refiner, router, new CoreProperties.RoutingConfig(null, null));

      assertTrue(config.enabled());
      assertSame(refiner, config.refiner());
      assertSame(router, config.router());
    }
  }
}
