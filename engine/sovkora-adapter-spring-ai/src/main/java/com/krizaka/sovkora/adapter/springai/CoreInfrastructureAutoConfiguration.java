package com.krizaka.sovkora.adapter.springai;

import com.krizaka.sovkora.core.application.engine.GraphEngine;
import com.krizaka.sovkora.core.application.processing.AudioPreProcessor;
import com.krizaka.sovkora.core.application.processing.VideoPreProcessor;
import com.krizaka.sovkora.core.domain.ports.outbound.AdminRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.domain.ports.outbound.CapabilityProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.infrastructure.adapter.processor.LocalAudioProcessor;
import com.krizaka.sovkora.core.infrastructure.adapter.processor.LocalVideoProcessor;
import com.krizaka.sovkora.core.infrastructure.adapter.processor.ProviderEndpointResolver;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring wiring of the core's configuration and infrastructure beans (ADR-092 §4 rule 1,
 * sovkora-engine#121): the core holds the plain records, this binds them.
 *
 * <p>Defines configuration properties ({@link CoreProperties}), the {@link GraphEngine}, and
 * infrastructure beans. AI model beans (chat/speech) are DB-driven routing beans in the {@code
 * infrastructure.provider} package (ADR-030: {@code RoutedChatModel} / {@code
 * RoutedTextToSpeechModel} backed by {@code DynamicModelRouter}), not static wiring here.
 *
 * @see CoreProperties
 */
@AutoConfiguration(before = CoreServicesAutoConfiguration.class)
public class CoreInfrastructureAutoConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(CoreInfrastructureAutoConfiguration.class);

  private static final String DEFAULT_PROVIDER = "ollama";
  private static final String DEFAULT_REFINER_MODEL = "llama3.2:3b";

  /**
   * Binds the {@code orazaka.core} configuration prefix to a {@link CoreProperties} record.
   *
   * @param env The Spring environment for property resolution.
   * @return The bound CoreProperties instance.
   * @throws IllegalStateException If the configuration prefix is missing or binding fails.
   */
  @Bean
  public CoreProperties coreProperties(Environment env) {
    CoreProperties bound = bindCore(env);

    // Master pipeline switch + routing come from yaml; per-interceptor enable is DB-driven
    // (pipeline_interceptor_config). The refiner/router model wiring is sourced from defaults
    // here, not yaml — keeping orchestration's refiner()/router() non-null for the AI stages.
    CoreProperties.OrchestrationConfig boundOrchestration = bound.orchestration();
    CoreProperties.OrchestrationConfig orchestration =
        new CoreProperties.OrchestrationConfig(
            boundOrchestration == null || boundOrchestration.enabled(),
            new CoreProperties.InterceptorConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, 0.7),
            new CoreProperties.InterceptorConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, 0.0),
            boundOrchestration == null
                ? new CoreProperties.RoutingConfig(null, null)
                : orDefault(
                    boundOrchestration.routing(), new CoreProperties.RoutingConfig(null, null)));

    CoreProperties.VideoConfig videoConfig =
        new CoreProperties.VideoConfig(
            new CoreProperties.VideoAnalysisConfig(8, 5),
            new CoreProperties.VideoGenerationConfig(
                "localai-video", "http://" + "local" + "host:8188"));

    return new CoreProperties(
        orDefault(bound.defaultProvider(), DEFAULT_PROVIDER),
        orDefault(bound.rag(), new CoreProperties.RagConfig(true, "pgvector", 3)),
        orDefault(bound.mcp(), new CoreProperties.McpConfig(List.of())),
        orchestration,
        videoConfig,
        imageConfig(bound),
        new CoreProperties.VisionConfig(DEFAULT_PROVIDER, "llama3.2-vision:latest"),
        new CoreProperties.AudioConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, "whisper-1"));
  }

  /** What the yaml says under {@code orazaka.core}; all-null when it says nothing. */
  private static CoreProperties bindCore(Environment env) {
    try {
      return Binder.get(env)
          .bind("orazaka.core", CoreProperties.class)
          .orElseGet(CoreInfrastructureAutoConfiguration::unbound);
    } catch (RuntimeException e) {
      logger.debug("Could not bind 'orazaka.core' properties, using defaults.", e);
      return unbound();
    }
  }

  private static CoreProperties unbound() {
    return new CoreProperties(null, null, null, null, null, null, null, null);
  }

  /**
   * Bound values first, the literals only where the yaml says nothing (ADR-062). This block used to
   * be literals alone and never read the bound image config: {@code steps} — what IMAGE_STEP prices
   * a generation on — was 20 whatever IMAGE_GEN_STEPS said, so a 10-step deployment billed double
   * what it did.
   */
  private static CoreProperties.ImageConfig imageConfig(CoreProperties bound) {
    CoreProperties.ImageGenerationConfig yaml =
        bound.image() == null ? null : bound.image().generation();
    return new CoreProperties.ImageConfig(
        new CoreProperties.ImageGenerationConfig(
            orDefault(yaml == null ? null : yaml.provider(), "localai-image"),
            orDefault(yaml == null ? null : yaml.baseUrl(), "http://" + "local" + "host:8086"),
            orDefault(yaml == null ? null : yaml.apiKey(), "not-required"),
            orDefault(yaml == null ? null : yaml.model(), "stable-diffusion"),
            orDefault(yaml == null ? null : yaml.width(), 512),
            orDefault(yaml == null ? null : yaml.height(), 512),
            orDefault(yaml == null ? null : yaml.n(), 1),
            // stable-diffusion.cpp's own default; the deployment states it via IMAGE_GEN_STEPS.
            orDefault(yaml == null ? null : yaml.steps(), 20),
            orDefault(yaml == null ? null : yaml.connectTimeoutMs(), 180000),
            orDefault(yaml == null ? null : yaml.readTimeoutMs(), 180000)));
  }

  /**
   * Binds {@code orazaka.security}: the governance kill-switch, off unless the property says so
   * (yaml, environment variable or system property).
   */
  @Bean
  public SecurityProperties securityProperties(Environment env) {
    return Binder.get(env)
        .bind("orazaka.security", SecurityProperties.class)
        .orElseGet(() -> new SecurityProperties(false));
  }

  /**
   * Synchronous executor for Chat/RAG SSE pipelines: Project Loom virtual threads (CQRS runtime
   * pool; the asynchronous runtime lives in the workers, behind RabbitMQ).
   */
  @Bean("synchronousExecutor")
  public ExecutorService synchronousExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * Registers the VideoPreProcessor bean.
   *
   * @param properties Core configuration properties.
   * @param catalogModelManager The catalog model manager.
   * @param whisperClient The shared Whisper transcription client.
   * @return A VideoPreProcessor instance.
   */
  @Bean
  public VideoPreProcessor videoPreProcessor(
      CoreProperties properties,
      ProviderEndpointResolver providerEndpointResolver,
      AudioTranscriber whisperClient) {
    return new LocalVideoProcessor(properties, providerEndpointResolver, whisperClient);
  }

  /**
   * Registers the AudioPreProcessor bean.
   *
   * @param properties Core configuration properties.
   * @param providerEndpointResolver Resolver for provider base URLs.
   * @param whisperClient The shared Whisper transcription client.
   * @return An AudioPreProcessor instance.
   */
  @Bean
  public AudioPreProcessor audioPreProcessor(
      CoreProperties properties,
      ProviderEndpointResolver providerEndpointResolver,
      AudioTranscriber whisperClient) {
    return new LocalAudioProcessor(properties, providerEndpointResolver, whisperClient);
  }

  /**
   * Registers the {@link ProviderEndpointResolver} bean, owning the catalog dependency used to
   * resolve provider base URLs for the local processors.
   *
   * @param catalogModelManager The catalog model manager.
   * @return A ProviderEndpointResolver instance.
   */
  @Bean
  public ProviderEndpointResolver providerEndpointResolver(
      CatalogModelManager catalogModelManager) {
    return new ProviderEndpointResolver(catalogModelManager);
  }

  /**
   * Creates the singleton {@link AdminRegistry} for runtime capability lock management.
   *
   * @return A new thread-safe admin registry instance.
   */
  @Bean
  public AdminRegistry adminRegistry() {
    return new AdminRegistry();
  }

  /**
   * Fallback Jackson 3 {@link ObjectMapper} bean. Spring Boot 4 auto-configures a Jackson 3 {@code
   * JsonMapper} (an {@code ObjectMapper} subtype) in full application contexts; this conditional
   * bean only materialises in slim contexts where that auto-configuration is absent, so adapters
   * that inject an {@code ObjectMapper} (e.g. {@code WhisperTranscriptionClient}, the router's
   * {@code McpController} / {@code JobListener}) always resolve one.
   *
   * @return A shared Jackson 3 ObjectMapper.
   */
  @Bean
  @Conditional(OnMissingObjectMapperCondition.class)
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * Creates the {@link GraphEngine} bean for SDUI Operation Graph compilation.
   *
   * @param capabilityProvider Database-backed source of capability descriptors (blueprint + enabled
   *     state). Resolved lazily; absent in contexts without the persistence adapter.
   * @param adminRegistry Runtime lock registry for dynamic capability control.
   * @param prober Asynchronous infrastructure status prober.
   * @param modelCatalogProvider Resolves the active chat model dynamically.
   * @return A configured graph engine instance.
   */
  @Bean
  public GraphEngine graphEngine(
      ObjectProvider<CapabilityProvider> capabilityProvider,
      AdminRegistry adminRegistry,
      InfrastructureStatusProvider prober,
      ModelCatalogProvider modelCatalogProvider) {
    return new GraphEngine(
        capabilityProvider.getIfAvailable(), adminRegistry, prober, modelCatalogProvider);
  }

  /** The bound value when the yaml gave one, the documented default when it did not. */
  private static <T> T orDefault(T bound, T fallback) {
    return bound != null ? bound : fallback;
  }
}
