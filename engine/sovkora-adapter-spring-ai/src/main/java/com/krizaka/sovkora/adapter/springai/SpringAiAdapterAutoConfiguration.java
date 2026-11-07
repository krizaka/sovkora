package com.krizaka.sovkora.adapter.springai;

import com.krizaka.sovkora.adapter.springai.chat.SpringAiChatInvocation;
import com.krizaka.sovkora.adapter.springai.chat.SpringAiProviderChatClient;
import com.krizaka.sovkora.adapter.springai.chat.SpringAiSpeechSynthesis;
import com.krizaka.sovkora.adapter.springai.client.AgentTurnsImpl;
import com.krizaka.sovkora.adapter.springai.client.ChatGeneratorClientImpl;
import com.krizaka.sovkora.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.sovkora.adapter.springai.image.SpringAiImageGeneratorClient;
import com.krizaka.sovkora.adapter.springai.memory.SpringAiToolAnswerCodec;
import com.krizaka.sovkora.adapter.springai.observability.FeatureTagObservationFilter;
import com.krizaka.sovkora.adapter.springai.prompt.OllamaOptionsStrategy;
import com.krizaka.sovkora.adapter.springai.prompt.OpenAiOptionsStrategy;
import com.krizaka.sovkora.adapter.springai.prompt.PipelineOptionsRegistry;
import com.krizaka.sovkora.adapter.springai.prompt.PipelineOptionsStrategy;
import com.krizaka.sovkora.adapter.springai.prompt.SpringAiPromptAssembly;
import com.krizaka.sovkora.adapter.springai.provider.AnthropicDynamicProvider;
import com.krizaka.sovkora.adapter.springai.provider.DynamicChatModelProvider;
import com.krizaka.sovkora.adapter.springai.provider.GeminiDynamicProvider;
import com.krizaka.sovkora.adapter.springai.provider.OpenAiDynamicProvider;
import com.krizaka.sovkora.adapter.springai.provider.SpringAiDynamicChatModelFactory;
import com.krizaka.sovkora.adapter.springai.routing.DynamicModelRouter;
import com.krizaka.sovkora.adapter.springai.routing.RoutedChatModel;
import com.krizaka.sovkora.adapter.springai.routing.RoutedTextToSpeechModel;
import com.krizaka.sovkora.adapter.springai.routing.UniversalProxyChatProvider;
import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.adapter.springai.structured.SpringAiStructuredOutputClient;
import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolOptions;
import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolRegistry;
import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolResolution;
import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.application.security.ClientToolGate;
import com.krizaka.sovkora.core.domain.ports.inbound.AgentTurns;
import com.krizaka.sovkora.core.domain.ports.outbound.*;
import com.krizaka.sovkora.core.infrastructure.adapter.security.LoggingAccessAudit;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Spring wiring of the Spring AI adapter (ADR-092 §4): the engine's ports on Spring AI, registered
 * unless the host brings its own.
 */
@AutoConfiguration
public class SpringAiAdapterAutoConfiguration {

  /**
   * The tool registry. Without a {@link ToolResultCache} bean, tool results are not cached.
   *
   * @param configProvider the platform's tool configuration (optional)
   * @param cache where cached results are kept (optional)
   * @param permissions the roles an administrator granted each tool (optional, ADR-091)
   * @return the registry
   */
  @Bean
  @ConditionalOnMissingBean(ToolRegistry.class)
  public SpringAiToolRegistry toolRegistry(
      ObjectProvider<PlatformToolConfigProvider> configProvider,
      ObjectProvider<ToolResultCache> cache,
      ObjectProvider<ToolPermissionProvider> permissions) {
    return new SpringAiToolRegistry(
        configProvider.getIfAvailable(),
        cache.getIfAvailable(NoToolResultCache::new),
        permissions.getIfAvailable());
  }

  /**
   * Where a refused read or tool call is written (ADR-091): a structured log line, unless the host
   * registers its own {@link AccessAudit}.
   *
   * @return the logging audit
   */
  @Bean
  @ConditionalOnMissingBean(AccessAudit.class)
  public LoggingAccessAudit accessAudit() {
    return new LoggingAccessAudit();
  }

  /**
   * The port that offers tools to a provider through its options.
   *
   * @return the adapter
   */
  @Bean
  @ConditionalOnMissingBean(ToolOptions.class)
  public SpringAiToolOptions toolOptions() {
    return new SpringAiToolOptions();
  }

  /**
   * The structured-output port, when exactly one chat model is wired.
   *
   * @param chatModel the model
   * @param bounds how many answers a call may ask for (the default of the port when none is wired)
   * @param observations where the chat client's observations go
   * @return the client
   */
  @Bean
  @ConditionalOnSingleCandidate(ChatModel.class)
  @ConditionalOnMissingBean(StructuredOutputClient.class)
  public SpringAiStructuredOutputClient structuredOutputClient(
      ChatModel chatModel,
      ObjectProvider<StructuredOutputBounds> bounds,
      ObjectProvider<ObservationRegistry> observations) {
    return new SpringAiStructuredOutputClient(
        chatModel,
        bounds.getIfAvailable(
            () -> StructuredOutputBounds.fixed(StructuredOutputBounds.DEFAULT_MAX_ATTEMPTS)),
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * The port that asks the model of a provider, over the provider-keyed {@link ChatModel} beans (an
   * empty set when there are none).
   *
   * @param chatModels the models by provider key
   * @param optionsRegistry builds the provider-specific options
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(ProviderChatClient.class)
  public SpringAiProviderChatClient providerChatClient(
      ObjectProvider<Map<String, ChatModel>> chatModels, PipelineOptionsRegistry optionsRegistry) {
    return new SpringAiProviderChatClient(chatModels.getIfAvailable(Map::of), optionsRegistry);
  }

  /**
   * Builds the OpenAI-compatible client of any endpoint (ADR-030).
   *
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean
  public UniversalProxyChatProvider universalProxyChatProvider(
      ObjectProvider<ObservationRegistry> observations) {
    return new UniversalProxyChatProvider(
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Resolves a {@link ChatModel} on demand from the catalogue (ADR-030), guarded by the spend
   * ceiling.
   *
   * @param resolver where a model name becomes an endpoint
   * @param proxy builds the client of an endpoint
   * @param spendGuard the daily budget
   * @return the router
   */
  @Bean
  @ConditionalOnMissingBean
  public DynamicModelRouter dynamicModelRouter(
      ModelEndpointResolver resolver, UniversalProxyChatProvider proxy, SpendGuard spendGuard) {
    return new DynamicModelRouter(resolver, proxy, spendGuard);
  }

  /**
   * The primary chat model: every call goes to the provider the catalogue binds its model to.
   *
   * @param router the router
   * @return the model
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public RoutedChatModel routedChatModel(DynamicModelRouter router) {
    return new RoutedChatModel(router);
  }

  /**
   * The primary text-to-speech model, routed by the catalogue like the chat model.
   *
   * @param resolver where a model name becomes an endpoint
   * @return the model
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public RoutedTextToSpeechModel routedTextToSpeechModel(
      ModelEndpointResolver resolver, ObjectProvider<ObservationRegistry> observations) {
    return new RoutedTextToSpeechModel(
        resolver, observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * The port that sends a prepared prompt to a model: the primary {@link ChatModel}, or one built
   * for a user's own key.
   *
   * @param chatModel the primary model
   * @param modelFactory builds a model for a user's key
   * @param bounds the tool-round bound per feature key, when a store is wired (else the default)
   * @param observations the host's registry, so each tool round is observed ({@code
   *     spring.ai.tool})
   * @param cachePolicy what a provider may cache per feature key, when a store is wired
   * @return the invocation
   */
  @Bean
  @ConditionalOnMissingBean(ChatInvocation.class)
  public SpringAiChatInvocation chatInvocation(
      ChatModel chatModel,
      DynamicChatModelFactory<ChatModel> modelFactory,
      ObjectProvider<ToolLoopBounds> bounds,
      ObjectProvider<ObservationRegistry> observations,
      ObjectProvider<PromptCachePolicy> cachePolicy) {
    return new SpringAiChatInvocation(
        chatModel,
        modelFactory,
        ToolCallingManager.builder()
            .observationRegistry(observations.getIfAvailable(() -> ObservationRegistry.NOOP))
            .build(),
        bounds.getIfAvailable(() -> ToolLoopBounds.fixed(ToolLoopBounds.DEFAULT_MAX_ROUNDS)),
        observations.getIfAvailable(() -> ObservationRegistry.NOOP),
        cachePolicy.getIfAvailable(
            () -> PromptCachePolicy.fixed(PromptCachePolicy.DEFAULT_STRATEGY)));
  }

  /**
   * Tags every {@code gen_ai} observation with the feature key of its turn (ADR-094 slice 3).
   * Spring Boot adds every {@link io.micrometer.observation.ObservationFilter} bean to the host's
   * registry.
   *
   * @return the filter
   */
  @Bean
  @ConditionalOnMissingBean
  public FeatureTagObservationFilter featureTagObservationFilter() {
    return new FeatureTagObservationFilter();
  }

  /**
   * The port that turns text into speech audio, over the primary text-to-speech model.
   *
   * @param model the primary model
   * @return the port
   */
  @Bean
  @ConditionalOnMissingBean(SpeechSynthesis.class)
  public SpringAiSpeechSynthesis speechSynthesis(TextToSpeechModel model) {
    return new SpringAiSpeechSynthesis(model);
  }

  /**
   * The port that stores a tool's answer as text for conversation memory.
   *
   * @return the codec
   */
  @Bean
  @ConditionalOnMissingBean(ToolAnswerCodec.class)
  public SpringAiToolAnswerCodec toolAnswerCodec() {
    return new SpringAiToolAnswerCodec();
  }

  /**
   * The image port, over the catalogue's image models (stable-diffusion.cpp dialect of the OpenAI
   * images API).
   *
   * @param properties core configuration
   * @param catalogModelManager the catalogue, which names the active image model and its provider
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(ImageGeneratorClient.class)
  public SpringAiImageGeneratorClient imageGeneratorClient(
      CoreProperties properties, CatalogModelManager catalogModelManager) {
    return new SpringAiImageGeneratorClient(properties, catalogModelManager);
  }

  /**
   * The chat port: a request runs through the engine.
   *
   * @param engine the engine
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(ChatGeneratorClient.class)
  public ChatGeneratorClientImpl chatGeneratorClientImpl(Engine engine) {
    return new ChatGeneratorClientImpl(engine);
  }

  /**
   * The agent-turn port (ADR-093 §4): a client-run agent's model turn through the engine.
   *
   * @param engine the engine
   * @param models the routing by feature key
   * @return the port
   */
  @Bean
  @ConditionalOnMissingBean(AgentTurns.class)
  public AgentTurnsImpl agentTurns(Engine engine, ModelEndpointResolver models) {
    return new AgentTurnsImpl(engine, models);
  }

  /**
   * The embedding port, over any OpenAI-compatible {@code /v1/embeddings} endpoint.
   *
   * @param endpoints where the embedding model lives
   * @param spendGuard the daily budget of paid providers
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(EmbeddingClient.class)
  public EmbeddingClientImpl embeddingClientImpl(
      ModelEndpointResolver endpoints,
      SpendGuard spendGuard,
      ObjectProvider<ObservationRegistry> observations) {
    return new EmbeddingClientImpl(
        endpoints, spendGuard, observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Builds the Anthropic (Claude) model of a user's own key.
   *
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean
  public AnthropicDynamicProvider anthropicDynamicProvider(
      ObjectProvider<ObservationRegistry> observations) {
    return new AnthropicDynamicProvider(
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Builds the Gemini model of a user's own key.
   *
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean
  public GeminiDynamicProvider geminiDynamicProvider(
      ObjectProvider<ObservationRegistry> observations) {
    return new GeminiDynamicProvider(observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Builds the OpenAI model of a user's own key.
   *
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean
  public OpenAiDynamicProvider openAiDynamicProvider(
      ObjectProvider<ObservationRegistry> observations) {
    return new OpenAiDynamicProvider(observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * The port that builds, per request, the model of a commercial provider from the user's key.
   *
   * @param providers every provider strategy wired
   * @return the factory
   */
  @Bean
  @ConditionalOnMissingBean(DynamicChatModelFactory.class)
  public SpringAiDynamicChatModelFactory dynamicChatModelFactory(
      List<DynamicChatModelProvider> providers) {
    return new SpringAiDynamicChatModelFactory(providers);
  }

  /** No cache: every call runs the tool. */
  private static final class NoToolResultCache implements ToolResultCache {
    @Override
    public String get(String toolId, String cacheKey) {
      return null;
    }

    @Override
    public void put(String toolId, String cacheKey, String value, long ttlSeconds) {
      // nothing is kept
    }
  }

  /** The prompt assembly on Spring AI (#121). */
  @Bean
  @ConditionalOnMissingBean(PromptAssembly.class)
  public PromptAssembly promptAssembly() {
    return new SpringAiPromptAssembly();
  }

  /**
   * The port that resolves the tools of a turn: the registry's, the platform's MCP servers and the
   * user's own (#121). Roles and the denials audit stay at the tool (ADR-091).
   *
   * @param platform the platform's MCP servers (optional)
   * @param user the user's own MCP servers (optional)
   * @param registry the local tools (optional)
   * @return the resolution
   */
  @Bean
  @ConditionalOnMissingBean(ToolResolution.class)
  public SpringAiToolResolution toolResolution(
      ObjectProvider<PlatformMcpServerProvider> platform,
      ObjectProvider<UserMcpServerProvider> user,
      ObjectProvider<ToolRegistry> registry,
      ObjectProvider<ToolPermissionProvider> permissions,
      AccessAudit audit) {
    // The tools the client runs (ADR-093 §4) are checked the way the engine's own are (ADR-091).
    return new SpringAiToolResolution(
        platform.getIfAvailable(),
        user.getIfAvailable(),
        registry.getIfAvailable(),
        new ClientToolGate(permissions.getIfAvailable(), audit));
  }

  /** The Ollama options strategy. */
  @Bean
  @ConditionalOnMissingBean(OllamaOptionsStrategy.class)
  public OllamaOptionsStrategy ollamaOptionsStrategy() {
    return new OllamaOptionsStrategy();
  }

  /** The OpenAI options strategy. */
  @Bean
  @ConditionalOnMissingBean(OpenAiOptionsStrategy.class)
  public OpenAiOptionsStrategy openAiOptionsStrategy() {
    return new OpenAiOptionsStrategy();
  }

  /** The registry that picks the options strategy of a provider. */
  @Bean
  @ConditionalOnMissingBean(PipelineOptionsRegistry.class)
  public PipelineOptionsRegistry pipelineOptionsRegistry(List<PipelineOptionsStrategy> strategies) {
    return new PipelineOptionsRegistry(strategies);
  }
}
