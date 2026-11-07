package com.krizaka.sovkora.adapter.springai;

import com.krizaka.sovkora.adapter.springai.memory.PersistedChatMemoryStore;
import com.krizaka.sovkora.adapter.springai.remote.HttpKnowledgeAdapter;
import com.krizaka.sovkora.adapter.springai.remote.InfrastructureProber;
import com.krizaka.sovkora.adapter.springai.remote.KnowledgeProperties;
import com.krizaka.sovkora.adapter.springai.remote.LocalAiSemanticClassifierAdapter;
import com.krizaka.sovkora.adapter.springai.remote.OllamaModelCatalogProvider;
import com.krizaka.sovkora.adapter.springai.remote.WhisperTranscriptionClient;
import com.krizaka.sovkora.adapter.springai.routing.CatalogModelEndpointResolver;
import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.application.engine.EngineInfrastructure;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.pipeline.PipelineRegistry;
import com.krizaka.sovkora.core.application.routing.PolicyStepRouter;
import com.krizaka.sovkora.core.application.routing.SemanticRoutingEngine;
import com.krizaka.sovkora.core.application.service.DefaultAiClient;
import com.krizaka.sovkora.core.application.service.DefaultCatalogModelService;
import com.krizaka.sovkora.core.application.service.DefaultChatSessionService;
import com.krizaka.sovkora.core.application.service.DefaultJobService;
import com.krizaka.sovkora.core.application.service.DefaultMcpService;
import com.krizaka.sovkora.core.domain.model.MediaCategory;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import com.krizaka.sovkora.core.domain.ports.inbound.CatalogModelService;
import com.krizaka.sovkora.core.domain.ports.inbound.ChatSessionService;
import com.krizaka.sovkora.core.domain.ports.inbound.JobService;
import com.krizaka.sovkora.core.domain.ports.inbound.McpService;
import com.krizaka.sovkora.core.domain.ports.inbound.StepRouter;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatMemoryStore;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.FeatureModelRoutes;
import com.krizaka.sovkora.core.domain.ports.outbound.ImageGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly;
import com.krizaka.sovkora.core.domain.ports.outbound.SemanticClassifierPort;
import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.sovkora.core.domain.ports.outbound.StepRouteProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolAnswerCodec;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.sovkora.core.infrastructure.adapter.ai.DefaultAudioGeneratorClient;
import com.krizaka.sovkora.core.infrastructure.adapter.routing.RuntimeStepRouteProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import com.krizaka.sovkora.core.infrastructure.host.HostCapabilityDetector;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.JobPersistenceProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring wiring of the core services (ADR-092 §4 rule 1, sovkora-engine#121): the core classes are
 * plain Java, registered here unless the host brings its own bean of the same type.
 */
@AutoConfiguration(after = SpringAiAdapterAutoConfiguration.class)
@EnableConfigurationProperties(KnowledgeProperties.class)
public class CoreServicesAutoConfiguration {

  /**
   * The port through which the core announces its domain events: Spring's own publisher, so
   * listeners such as the settlement of a chat keep working.
   *
   * @param publisher Spring's event publisher
   * @return the port
   */
  @Bean
  @ConditionalOnMissingBean(DomainEventPublisher.class)
  public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher publisher) {
    return publisher::publishEvent;
  }

  /**
   * The conversation memories, durable when the host brings a store or the persistence provider.
   *
   * @param store the host's own durable store, if any (see {@link #chatMemoryStore})
   * @return the resolver
   */
  @Bean
  @ConditionalOnMissingBean
  public MemoryResolver memoryResolver(ObjectProvider<ChatMemoryStore> store) {
    return new MemoryResolver(store.getIfAvailable());
  }

  /**
   * The durable chat memory on the persistence module, for hosts that keep chat messages (the
   * conversation service injects it). Absent when the host has no persisted chat memory.
   *
   * @param persistence the persisted chat memory
   * @param codec how tool answers are stored
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(ChatMemoryStore.class)
  @ConditionalOnBean({ChatMemoryPersistenceProvider.class, ToolAnswerCodec.class})
  public ChatMemoryStore chatMemoryStore(
      ChatMemoryPersistenceProvider persistence, ToolAnswerCodec codec) {
    return new PersistedChatMemoryStore(persistence, codec);
  }

  /**
   * The pipeline configuration, reloadable at run time.
   *
   * @param config the persisted configuration
   * @return the registry
   */
  @Bean
  @ConditionalOnMissingBean
  public PipelineRegistry pipelineRegistry(PipelineConfigProvider config) {
    return new PipelineRegistry(config);
  }

  /**
   * The two-phase pipeline executor.
   *
   * @param interceptors the interceptors of the host
   * @param registry the pipeline configuration
   * @param routing the semantic routing engine
   * @param security the security properties
   * @param properties the core properties
   * @param meters the meter registry
   * @param events where stage events go
   * @return the executor
   */
  @Bean
  @ConditionalOnMissingBean
  public DynamicPipelineExecutor dynamicPipelineExecutor(
      List<PromptContextInterceptor> interceptors,
      PipelineRegistry registry,
      SemanticRoutingEngine routing,
      SecurityProperties security,
      CoreProperties properties,
      MeterRegistry meters,
      DomainEventPublisher events) {
    return new DynamicPipelineExecutor(
        interceptors, registry, routing, security, properties, meters, events);
  }

  /**
   * Compiles the context of a turn.
   *
   * @param catalog the model catalogue
   * @param tools the tool resolution port
   * @param prompt the prompt assembly port
   * @return the bridge
   */
  @Bean
  @ConditionalOnMissingBean
  public EnginePipelineBridge enginePipelineBridge(
      ModelCatalogProvider catalog, ToolResolution tools, PromptAssembly prompt) {
    return new EnginePipelineBridge(catalog, tools, prompt);
  }

  /**
   * Streams a turn chunk by chunk.
   *
   * @param invocation the chat invocation port
   * @param pipeline the pipeline bridge
   * @param credentials the user credentials port
   * @return the bridge
   */
  @Bean
  @ConditionalOnMissingBean
  public EngineStreamBridge engineStreamBridge(
      ChatInvocation invocation,
      EnginePipelineBridge pipeline,
      UserCredentialsProvider credentials) {
    return new EngineStreamBridge(invocation, pipeline, credentials);
  }

  /**
   * What the engine needs around a turn: catalogue, bridges and credentials.
   *
   * @param catalog the model catalogue
   * @param pipelineBridge the pipeline bridge
   * @param streamBridge the stream bridge
   * @param credentials the user credentials port
   * @return the grouped infrastructure
   */
  @Bean
  @ConditionalOnMissingBean
  public EngineInfrastructure engineInfrastructure(
      ModelCatalogProvider catalog,
      EnginePipelineBridge pipelineBridge,
      EngineStreamBridge streamBridge,
      UserCredentialsProvider credentials) {
    return new EngineInfrastructure(catalog, pipelineBridge, streamBridge, credentials);
  }

  /**
   * The engine: one turn from request to response.
   *
   * @param invocation the chat invocation port
   * @param speech the speech port
   * @param properties the core properties
   * @param interceptors the interceptors of the host
   * @param executor the pipeline executor
   * @param events where domain events go
   * @param infrastructure the infrastructure around a turn
   * @return the engine
   */
  @Bean
  @ConditionalOnMissingBean
  public Engine engine(
      ChatInvocation invocation,
      SpeechSynthesis speech,
      CoreProperties properties,
      List<PromptContextInterceptor> interceptors,
      DynamicPipelineExecutor executor,
      DomainEventPublisher events,
      EngineInfrastructure infrastructure) {
    return new Engine(
        invocation, speech, properties, interceptors, executor, events, infrastructure);
  }

  /**
   * The semantic classifier, over a LocalAI-compatible endpoint.
   *
   * @param builder the REST client builder
   * @param properties the core properties
   * @return the classifier
   */
  @Bean
  @ConditionalOnMissingBean(SemanticClassifierPort.class)
  public LocalAiSemanticClassifierAdapter semanticClassifier(
      RestClient.Builder builder, CoreProperties properties) {
    return new LocalAiSemanticClassifierAdapter(builder, properties);
  }

  /**
   * The catalogue of the models the local Ollama serves.
   *
   * @param builder the REST client builder
   * @param baseUrl the Ollama base URL
   * @param activeModel the model in use, if configured
   * @param catalog the model catalogue, whose chat default beats Ollama's list order
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean(ModelCatalogProvider.class)
  public OllamaModelCatalogProvider ollamaModelCatalogProvider(
      RestClient.Builder builder,
      @Value("${spring.ai.ollama.base-url}") String baseUrl,
      @Value("${ORAZAKA_OLLAMA_MODEL:${spring.ai.ollama.chat.options.model:}}") String activeModel,
      ObjectProvider<CatalogModelManager> catalog) {
    return new OllamaModelCatalogProvider(
        builder,
        baseUrl,
        activeModel,
        () ->
            Optional.ofNullable(catalog.getIfAvailable())
                .flatMap(c -> c.getDefaultModelByCategory(MediaCategory.CHAT.value()))
                .map(CatalogModelDto::modelName));
  }

  /**
   * The knowledge service (RAG), over its internal HTTP API.
   *
   * @param properties where the knowledge service is
   * @return the adapter
   */
  @Bean
  @ConditionalOnMissingBean(KnowledgeService.class)
  public HttpKnowledgeAdapter knowledgeService(KnowledgeProperties properties) {
    return new HttpKnowledgeAdapter(properties);
  }

  /**
   * Resolves a model name to its endpoint through the catalogue and the routing rows.
   *
   * @param catalog the catalogue manager
   * @param routes the routing rows, when the deployment has them
   * @return the resolver
   */
  @Bean
  @ConditionalOnMissingBean(ModelEndpointResolver.class)
  public CatalogModelEndpointResolver modelEndpointResolver(
      CatalogModelManager catalog, ObjectProvider<FeatureModelRoutes> routes) {
    return new CatalogModelEndpointResolver(catalog, routes);
  }

  /**
   * The client of the platform: chat, audio and image behind one port.
   *
   * @param chat the chat generator
   * @param audio the audio generator
   * @param image the image generator
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(AiClient.class)
  public DefaultAiClient aiClient(
      ChatGeneratorClient chat, AudioGeneratorClient audio, ImageGeneratorClient image) {
    return new DefaultAiClient(chat, audio, image);
  }

  /**
   * The audio generation port, over the engine.
   *
   * @param engine the engine
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(AudioGeneratorClient.class)
  public DefaultAudioGeneratorClient audioGeneratorClient(Engine engine) {
    return new DefaultAudioGeneratorClient(engine);
  }

  /**
   * The catalogue of models, read through the persistence port.
   *
   * @param manager the catalogue manager
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(CatalogModelService.class)
  public DefaultCatalogModelService catalogModelService(CatalogModelManager manager) {
    return new DefaultCatalogModelService(manager);
  }

  /**
   * The chat sessions, read through the persistence port.
   *
   * @param provider the persistence provider
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(ChatSessionService.class)
  public DefaultChatSessionService chatSessionService(ChatSessionPersistenceProvider provider) {
    return new DefaultChatSessionService(provider);
  }

  /**
   * The asynchronous jobs service: persists the job, then announces its new state.
   *
   * @param provider the job persistence provider
   * @param events where the status change is announced
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(JobService.class)
  public DefaultJobService jobService(
      JobPersistenceProvider provider, DomainEventPublisher events) {
    return new DefaultJobService(provider, events);
  }

  /**
   * The Whisper client behind the transcription port, used by the local audio and video processors.
   *
   * @param restClientBuilder the RestClient builder
   * @param objectMapper the Jackson mapper
   * @return the transcriber
   */
  @Bean
  @ConditionalOnMissingBean(AudioTranscriber.class)
  public WhisperTranscriptionClient whisperTranscriptionClient(
      RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    return new WhisperTranscriptionClient(restClientBuilder, objectMapper);
  }

  /**
   * The background prober of the video and image engines (scheduled where the host enables
   * scheduling).
   *
   * @param properties the core properties
   * @param videoProbePort the port probed for the video engine
   * @param imageProbePort the port probed for the image engine
   * @return the prober
   */
  @Bean
  @ConditionalOnMissingBean(InfrastructureStatusProvider.class)
  public InfrastructureProber infrastructureProber(
      CoreProperties properties,
      @Value("${orazaka.core.video.generation.probe-port:8188}") int videoProbePort,
      @Value("${orazaka.core.image.generation.probe-port:8086}") int imageProbePort) {
    return new InfrastructureProber(properties, videoProbePort, imageProbePort);
  }

  /**
   * The daily budget breaker of paid providers; a negative budget switches it off.
   *
   * @param budgetUsd the daily ceiling in USD
   * @param chatUsd estimated cost of a chat call
   * @param embeddingUsd estimated cost of an embedding call
   * @param videoUsd estimated cost of a video call
   * @return the guard
   */
  @Bean
  @ConditionalOnMissingBean
  public SpendGuard spendGuard(
      @Value("${orazaka.spend.daily-budget-usd:${AI_SPEND_DAILY_BUDGET_USD:5.0}}") double budgetUsd,
      @Value("${orazaka.spend.cost-usd.chat:0.01}") double chatUsd,
      @Value("${orazaka.spend.cost-usd.embedding:0.0002}") double embeddingUsd,
      @Value("${orazaka.spend.cost-usd.video:0.60}") double videoUsd) {
    return new SpendGuard(budgetUsd, chatUsd, embeddingUsd, videoUsd, java.time.Clock.systemUTC());
  }

  /**
   * The MCP tool service (ADR-091: roles and denials are audited at the tool).
   *
   * @param registry the tool registry
   * @param audit where a refusal is written
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(McpService.class)
  public DefaultMcpService coreMcpService(ToolRegistry registry, AccessAudit audit) {
    return new DefaultMcpService(registry, audit);
  }

  /**
   * The routes of a step, read from the runtime configuration and the catalogue.
   *
   * @param config the runtime configuration
   * @param catalog the catalogue manager
   * @param mapper the JSON mapper
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean(StepRouteProvider.class)
  public RuntimeStepRouteProvider stepRouteProvider(
      RuntimeConfigProvider config, CatalogModelManager catalog, ObjectMapper mapper) {
    return new RuntimeStepRouteProvider(config, catalog, mapper);
  }

  /**
   * The router that picks a step's model by policy.
   *
   * @param routes the routes of a step
   * @return the router
   */
  @Bean
  @ConditionalOnMissingBean(StepRouter.class)
  public PolicyStepRouter stepRouter(StepRouteProvider routes) {
    return new PolicyStepRouter(routes);
  }

  /**
   * The semantic routing engine, over the classifier port.
   *
   * @param classifier the classifier
   * @return the engine
   */
  @Bean
  @ConditionalOnMissingBean
  public SemanticRoutingEngine semanticRoutingEngine(SemanticClassifierPort classifier) {
    return new SemanticRoutingEngine(classifier);
  }

  /**
   * What the host machine can run (Apple Silicon or CPU only).
   *
   * @return the detector
   */
  @Bean
  @ConditionalOnMissingBean
  public HostCapabilityDetector hostCapabilityDetector() {
    return new HostCapabilityDetector();
  }
}
