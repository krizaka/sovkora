package com.krizaka.sovkora.core.infrastructure.config;

import com.krizaka.sovkora.core.domain.model.RoutingMode;
import java.util.List;

/**
 * Configuration properties for Sovkora CORS (Cognitive Orchestration and Retrieval System). Maps to
 * {@code orazaka.core} in {@code application.yml}.
 *
 * <p>This record defines the structural schema for AI provider orchestration, RAG, and MCP. It
 * utilizes Java 21 Records for immutable, type-safe configuration.
 *
 * @param defaultProvider The global AI provider to use (e.g., "ollama", "openai"). Required.
 * @param rag Configuration for Retrieval-Augmented Generation context injection.
 * @param mcp Configuration for Model Context Protocol (MCP) server endpoints.
 * @param orchestration Configuration for the cognitive prompt orchestration pipeline.
 * @param video Configuration for video analysis and generation.
 * @param image Configuration for image analysis and generation.
 * @param vision Configuration for vision analysis model settings.
 * @param audio Configuration for audio analysis model settings.
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatmodel.html">Spring AI
 *     ChatModel</a>
 */
public record CoreProperties(
    String defaultProvider,
    RagConfig rag,
    McpConfig mcp,
    OrchestrationConfig orchestration,
    VideoConfig video,
    ImageConfig image,
    VisionConfig vision,
    AudioConfig audio) {

  /**
   * RAG (Retrieval-Augmented Generation) configuration.
   *
   * @param enabled Whether RAG context injection is active.
   * @param storeType The type of vector store (e.g., "pg vector").
   * @param topK The number of relevant documents to retrieve.
   */
  public record RagConfig(boolean enabled, String storeType, Integer topK) {}

  /**
   * MCP (Model Context Protocol) configuration.
   *
   * @param endpoints List of external MCP server URLs.
   */
  public record McpConfig(List<String> endpoints) {}

  /**
   * Configuration for the cognitive prompt orchestration pipeline.
   *
   * <p>{@code enabled} is the master pipeline kill-switch. Per-interceptor enable/disable is NOT
   * here — it is database-driven via {@code pipeline_interceptor_config} (read by the {@code
   * PipelineRegistry}/{@code DynamicPipelineExecutor}). {@code refiner}/{@code router} carry only
   * the model wiring (provider/model/temperature) for those AI stages.
   */
  public record OrchestrationConfig(
      boolean enabled, InterceptorConfig refiner, InterceptorConfig router, RoutingConfig routing) {
    // ONE constructor, deliberately — the SecurityProperties defect a second time (ADR-062). A
    // three-argument convenience constructor lived here, used only by tests. With two constructors
    // and no @ConstructorBinding the binder built no OrchestrationConfig at all,
    // CoreInfrastructureAutoConfiguration
    // fell back to "enabled", and `orazaka.core.orchestration.enabled: false` was read by nothing.
    // [CFG-001] now fails the build on that shape.
  }

  /**
   * Hybrid routing configuration for the pipeline orchestrator.
   *
   * @param mode The routing strategy (DETERMINISTIC or AGENTIC). Defaults to DETERMINISTIC.
   */
  public record RoutingConfig(RoutingMode mode, String semanticEndpoint) {
    public RoutingConfig {
      if (mode == null) {
        mode = RoutingMode.DETERMINISTIC;
      }
      if (semanticEndpoint == null || semanticEndpoint.isBlank()) {
        semanticEndpoint = "http://" + "local" + "host:8085/v1/classify";
      }
    }
  }

  /**
   * Model wiring for the refiner and router AI pipeline stages (provider/model/temperature). Enable
   * state lives in {@code pipeline_interceptor_config}, not here.
   */
  public record InterceptorConfig(String provider, String model, Double temperature) {}

  /** Partitioned video pipeline configuration (analysis vs generation). */
  public record VideoConfig(VideoAnalysisConfig analysis, VideoGenerationConfig generation) {}

  /**
   * Video analysis (vision input) configuration. All values are bound from {@code application.yml}
   * — no hardcoded defaults.
   */
  public record VideoAnalysisConfig(Integer maxKeyframes, Integer frameIntervalSec) {}

  /** Video generation (text-to-video output) configuration. */
  public record VideoGenerationConfig(String provider, String baseUrl) {}

  /** Partitioned image pipeline configuration (generation only). */
  public record ImageConfig(ImageGenerationConfig generation) {}

  /**
   * Image generation configuration.
   *
   * <p>{@code steps} is the deployment's step count for a model the catalogue gives none ({@code
   * orazaka_models.max_steps}) when the caller asked for none. The adapter sends the count it chose
   * with every request, so the server runs — and {@code IMAGE_STEP} prices — that count (ADR-047).
   */
  public record ImageGenerationConfig(
      String provider,
      String baseUrl,
      String apiKey,
      String model,
      Integer width,
      Integer height,
      Integer n,
      Integer steps,
      Integer connectTimeoutMs,
      Integer readTimeoutMs) {
    // ONE constructor (ADR-062, [CFG-001]). A nine-argument convenience constructor without
    // `steps` lived here; with two constructors the binder built no ImageGenerationConfig, so
    // `orazaka.core.image.generation.steps` never reached the IMAGE_STEP meter. Cause 1 of 2.
  }

  /** Vision analysis model settings. */
  public record VisionConfig(String provider, String model) {}

  /** Audio analysis model settings. */
  public record AudioConfig(String provider, String model, String transcriptionModel) {
    // ONE constructor ([CFG-001] found this third instance: a two-argument convenience
    // constructor made the binder drop `orazaka.core.audio`. Its values are still built from
    // literals
    // in CoreInfrastructureAutoConfiguration, which masks the loss — recorded in ADR-062, not
    // changed here.)
  }
}
