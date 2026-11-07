package com.krizaka.sovkora.core.application.engine;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.ChatModelInvocation;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.*;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Comprehensive unit tests for {@link Engine} covering chat, tool attachment, RAG injection, MCP
 * context, memory, and orchestration pipeline behavior.
 */
@ExtendWith(MockitoExtension.class)
class EngineTest {

  private static final Actor ACTOR = Actor.of("user-1", List.of("ROLE_USER")).orElseThrow();

  @Mock private ChatModel chatModel;
  @Mock private EmbeddingModel embeddingModel;
  @Mock private SpeechSynthesis speechModel;
  @Mock private ToolRegistry toolRegistry;
  @Mock private KnowledgeService knowledgeService;
  @Mock private McpOrchestrator mcpOrchestrator;
  @Mock private ConversationMemory mockChatMemory;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private ModelCatalogProvider modelCatalogProvider;
  @Mock private UserCredentialsProvider credentialsProvider;
  @Mock private DynamicChatModelFactory<ChatModel> modelFactory;
  @Mock private PlatformMcpServerProvider platformMcpServerProvider;
  @Mock private UserMcpServerProvider userMcpServerProvider;

  private Engine engine;
  private CoreProperties properties;
  private EnginePipelineBridge pipelineBridge;
  private EngineStreamBridge streamBridge;

  @BeforeEach
  void setUp() {
    properties =
        new CoreProperties(
            PROVIDER_OLLAMA,
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);

    var memoryInterceptor = createMemoryInterceptor();
    var mcpInterceptor = createMcpInterceptor(mcpOrchestrator);
    var ragInterceptor = createRagInterceptor(properties, knowledgeService);
    var toolInterceptor = createToolInterceptor(toolRegistry);

    lenient()
        .when(modelCatalogProvider.getActiveChatModel())
        .thenReturn(Optional.of("llama3.1:8b"));

    pipelineBridge =
        new EnginePipelineBridge(
            modelCatalogProvider,
            request ->
                com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.ResolvedTools.none(),
            new com.krizaka.sovkora.core.application.TestPromptAssembly());
    streamBridge =
        new EngineStreamBridge(
            ChatModelInvocation.of(chatModel, modelFactory), pipelineBridge, credentialsProvider);

    engine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            properties,
            List.of(ragInterceptor, mcpInterceptor, memoryInterceptor, toolInterceptor),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);
  }

  @Test
  void shouldExecuteChatRequest() {
    // Given
    InternalChatRequest request = InternalChatRequest.simple(PROMPT_HELLO_CAP);
    AssistantMessage assistantMessage = new AssistantMessage("Hi there!");
    Generation generation = new Generation(assistantMessage);
    ChatResponse chatResponse = new ChatResponse(List.of(generation));

    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    InternalChatResponse response = engine.chat(request);

    // Then
    assertThat(response.content()).isEqualTo("Hi there!");
    assertThat(response.metadata()).containsEntry("provider", PROVIDER_OLLAMA);
    verify(chatModel).call(any(Prompt.class));
    verify(eventPublisher).publish(any(Object.class));
  }

  @Test
  void shouldAttachPosterToolWhenDemanded() {
    // Given
    ToolCallback mockTool = mock(ToolCallback.class);
    ToolDefinition mockDef = mock(ToolDefinition.class);
    when(mockDef.name()).thenReturn(TOOL_ANALYZE_POSTER);
    when(mockTool.getToolDefinition()).thenReturn(mockDef);
    EngineTool engineTool = engineTool(mockTool);
    when(toolRegistry.getRegisteredTools(any())).thenReturn(List.of(engineTool));

    InternalChatRequest request =
        InternalChatRequest.simple("Please analyze this poster for visual themes");
    AssistantMessage assistantMessage = new AssistantMessage(RESPONSE);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    engine.chat(request);

    // Then
    verify(chatModel)
        .call(
            argThat(
                (Prompt prompt) -> {
                  var opts = prompt.getOptions();
                  if (opts instanceof OllamaChatOptions ollama) {
                    // Spring AI 2.0 dropped tool *names* on options — tools are carried as
                    // callbacks.
                    return ollama.getToolCallbacks() != null
                        && ollama.getToolCallbacks().contains(mockTool);
                  }
                  return false;
                }));
  }

  @Test
  void shouldNotAttachPosterToolWhenNotDemanded() {
    // Given
    ToolCallback mockTool = mock(ToolCallback.class);
    ToolDefinition mockDef = mock(ToolDefinition.class);
    when(mockDef.name()).thenReturn(TOOL_ANALYZE_POSTER);
    when(mockTool.getToolDefinition()).thenReturn(mockDef);
    EngineTool engineTool = engineTool(mockTool);
    when(toolRegistry.getRegisteredTools(any())).thenReturn(List.of(engineTool));

    InternalChatRequest request = InternalChatRequest.simple("Hello there, how are you?");
    AssistantMessage assistantMessage = new AssistantMessage(RESPONSE);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    engine.chat(request);

    // Then
    verify(chatModel)
        .call(
            argThat(
                (Prompt prompt) -> {
                  var opts = prompt.getOptions();
                  if (opts instanceof OllamaChatOptions ollama) {
                    return ollama.getToolCallbacks() == null
                        || ollama.getToolCallbacks().stream()
                            .noneMatch(
                                t -> TOOL_ANALYZE_POSTER.equals(t.getToolDefinition().name()));
                  }
                  return true;
                }));
  }

  @Test
  void shouldInjectRagContextWhenEnabled() {
    // Given
    properties =
        new CoreProperties(
            PROVIDER_OLLAMA,
            new CoreProperties.RagConfig(true, "pgvector", 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);

    var memoryInterceptor = createMemoryInterceptor();
    var mcpInterceptor = createMcpInterceptor(mcpOrchestrator);
    var ragInterceptor = createRagInterceptor(properties, knowledgeService);
    var toolInterceptor = createToolInterceptor(toolRegistry);

    engine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            properties,
            List.of(ragInterceptor, mcpInterceptor, memoryInterceptor, toolInterceptor),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    InternalChatRequest request = InternalChatRequest.simple(PROMPT_HELLO_CAP);
    when(knowledgeService.retrieveContext(any(Actor.class), anyString(), anyInt()))
        .thenReturn("Relevant context");

    AssistantMessage assistantMessage = new AssistantMessage(RESPONSE);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    engine.chat(request);

    // Then
    verify(knowledgeService).retrieveContext(ACTOR, PROMPT_HELLO_CAP, 3);
    verify(chatModel)
        .call(
            argThat(
                (Prompt prompt) ->
                    prompt.getInstructions().stream()
                        .anyMatch(m -> m.getText().contains("RAG Context"))));
  }

  @Test
  void shouldInjectMcpContextWhenAvailable() {
    // Given
    InternalChatRequest request = InternalChatRequest.simple(PROMPT_HELLO_CAP);
    when(mcpOrchestrator.resolveExternalContext()).thenReturn("External knowledge");

    AssistantMessage assistantMessage = new AssistantMessage(RESPONSE);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    engine.chat(request);

    // Then
    verify(mcpOrchestrator).resolveExternalContext();
    verify(chatModel)
        .call(
            argThat(
                (Prompt prompt) ->
                    prompt.getInstructions().stream()
                        .anyMatch(m -> m.getText().contains("MCP Context"))));
  }

  @Test
  void shouldPrependHistoryBeforeCurrentPrompt() throws Exception {
    // Given
    UserMessage oldUserMsg = new UserMessage("Old user prompt");
    AssistantMessage oldAssistantMsg = new AssistantMessage("Old assistant reply");

    Object memoryResolver = createMemoryResolver();
    when(mockChatMemory.get(SESSION_1))
        .thenReturn(
            List.of(
                EngineMessage.user(oldUserMsg.getText()),
                EngineMessage.assistant(oldAssistantMsg.getText())));

    // Inject the mock ConversationMemory into the resolver via reflection
    var sessionsField = memoryResolver.getClass().getDeclaredField("sessionMemories");
    sessionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    var sessions = (Map<String, ConversationMemory>) sessionsField.get(memoryResolver);
    sessions.put(SESSION_1, mockChatMemory);

    var memoryInterceptor = createMemoryInterceptorWith(memoryResolver);
    var mcpInterceptor = createMcpInterceptor(mcpOrchestrator);
    var ragInterceptor = createRagInterceptor(properties, knowledgeService);
    var toolInterceptor = createToolInterceptor(toolRegistry);

    Engine historyEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            properties,
            List.of(ragInterceptor, mcpInterceptor, memoryInterceptor, toolInterceptor),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    Context context = new Context("user-123", SESSION_1, Map.of(), Set.of());
    InternalChatRequest request = new InternalChatRequest("New prompt", null, null, context);

    AssistantMessage assistantMessage = new AssistantMessage(RESPONSE);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    historyEngine.chat(request);

    // Then
    verify(chatModel)
        .call(
            argThat(
                (Prompt prompt) -> {
                  List<Message> instructions = prompt.getInstructions();
                  if (instructions.size() != 3) {
                    return false;
                  }
                  return "Old user prompt".equals(instructions.getFirst().getText())
                      && "Old assistant reply".equals(instructions.get(1).getText())
                      && "New prompt".equals(instructions.get(2).getText());
                }));
  }

  @Test
  void shouldThrowExceptionWhenProviderMissing() {
    // Given
    CoreProperties emptyProps =
        new CoreProperties(
            null,
            null,
            null,
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);
    Engine badEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            emptyProps,
            List.of(),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    // When / Then
    var simpleReq = InternalChatRequest.simple("test");
    Assertions.assertThrows(CoreException.class, () -> badEngine.chat(simpleReq));
  }

  @Test
  void shouldBypassDynamicPipelineExecutorWhenDisabled() {
    // Given
    InternalChatRequest request = InternalChatRequest.simple(FUZZY_PROMPT);
    AssistantMessage assistantMessage = new AssistantMessage("Engine response");
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(assistantMessage))));

    // When
    this.engine.chat(request);

    // Then
    assertThat(engine.chat(request).content()).isEqualTo("Engine response");
    verify(chatModel, times(2))
        .call(
            argThat(
                (Prompt prompt) ->
                    prompt.getInstructions().stream()
                        .anyMatch(m -> m.getText().contains(FUZZY_PROMPT))));
  }

  @Test
  void shouldExecutePipelineWhenEnabled() {
    // Given
    CoreProperties.OrchestrationConfig orchConfig =
        new CoreProperties.OrchestrationConfig(
            true,
            new CoreProperties.InterceptorConfig(PROVIDER_OLLAMA, "llama-refine", 0.2),
            new CoreProperties.InterceptorConfig(PROVIDER_OLLAMA, "llama-route", 0.0),
            new CoreProperties.RoutingConfig(null, null));
    CoreProperties enabledProps =
        new CoreProperties(
            PROVIDER_OLLAMA,
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            orchConfig,
            null,
            null,
            null,
            null);

    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(new Generation(new AssistantMessage("Refined prompt instruction")))));

    DynamicPipelineExecutor pipeline = mock(DynamicPipelineExecutor.class);
    when(pipeline.process(anyString(), anyInt(), any(Context.class)))
        .thenReturn(new PromptContext(FUZZY_PROMPT, Map.of()));

    var memoryInterceptor = createMemoryInterceptor();
    var mcpInterceptor = createMcpInterceptor(mcpOrchestrator);
    var ragInterceptor = createRagInterceptor(enabledProps, knowledgeService);
    var toolInterceptor = createToolInterceptor(toolRegistry);

    Engine customEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            enabledProps,
            List.of(ragInterceptor, mcpInterceptor, memoryInterceptor, toolInterceptor),
            pipeline,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    InternalChatRequest request = InternalChatRequest.simple(FUZZY_PROMPT);

    // When
    InternalChatResponse response = customEngine.chat(request);

    // Then — Pipeline interceptors created via reflection don't have @Value resources injected,
    // so refiner/router bypass. The first mock response from the default provider (ollama) is
    // returned.
    assertThat(response.content()).isEqualTo("Refined prompt instruction");
    assertThat(response.metadata()).containsEntry("provider", PROVIDER_OLLAMA);
  }

  /**
   * Creates a stub MemoryInterceptor using the default MemoryResolver.
   *
   * <p>Replaces the previous reflective factory that used {@code Class.forName()} to load the
   * concrete MemoryInterceptor from the enrichment submodule. Core tests must not depend on
   * concrete interceptor implementations [ERR-122].
   */
  private PromptContextInterceptor createMemoryInterceptor() {
    com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver resolver =
        new com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver();
    return createMemoryInterceptorWith(resolver);
  }

  /**
   * Creates a stub MemoryInterceptor with a given MemoryResolver, inlining the memory prepend and
   * persist behavior for test isolation.
   */
  private PromptContextInterceptor createMemoryInterceptorWith(Object resolver) {
    com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver memoryResolver =
        (com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver) resolver;
    return new PromptContextInterceptor() {
      @Override
      public EngineChatOptions preProcess(
          InternalChatRequest request,
          String promptText,
          List<EngineMessage> messages,
          EngineChatOptions options) {
        Optional.ofNullable(request.context())
            .map(com.krizaka.sovkora.core.domain.model.Context::conversationId)
            .filter(id -> !id.isBlank())
            .ifPresent(
                convId -> {
                  var history = memoryResolver.resolve(convId).get(convId);
                  if (!history.isEmpty()) {
                    messages.addAll(0, history);
                  }
                });
        return options;
      }

      @Override
      public void postProcess(InternalChatRequest request, String promptText, String responseText) {
        Optional.ofNullable(request.context())
            .map(com.krizaka.sovkora.core.domain.model.Context::conversationId)
            .filter(id -> !id.isBlank())
            .ifPresent(
                convId -> {
                  var chatMemory = memoryResolver.resolve(convId);
                  List<EngineMessage> newMessages = new java.util.ArrayList<>();
                  if (promptText != null && !promptText.isBlank()) {
                    newMessages.add(EngineMessage.user(promptText));
                  }
                  newMessages.add(EngineMessage.assistant(responseText));
                  chatMemory.add(convId, newMessages);
                });
      }
    };
  }

  private Object createMemoryResolver() {
    return new com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver();
  }

  /** Creates a stub MCP interceptor that injects external context from the orchestrator. */
  private PromptContextInterceptor createMcpInterceptor(McpOrchestrator orchestrator) {
    return new PromptContextInterceptor() {
      @Override
      public EngineChatOptions preProcess(
          InternalChatRequest request,
          String promptText,
          List<EngineMessage> messages,
          EngineChatOptions options) {
        Optional.ofNullable(orchestrator.resolveExternalContext())
            .filter(ctx -> !ctx.isBlank())
            .ifPresent(ctx -> messages.addFirst(EngineMessage.system("MCP Context: " + ctx)));
        return options;
      }
    };
  }

  /** Creates a stub RAG interceptor that injects knowledge base context when enabled. */
  private PromptContextInterceptor createRagInterceptor(
      CoreProperties properties, KnowledgeService knowledgeService) {
    return new PromptContextInterceptor() {
      @Override
      public EngineChatOptions preProcess(
          InternalChatRequest request,
          String promptText,
          List<EngineMessage> messages,
          EngineChatOptions options) {
        Optional.ofNullable(properties.rag())
            .filter(CoreProperties.RagConfig::enabled)
            .ifPresent(
                rag -> {
                  String context = knowledgeService.retrieveContext(ACTOR, promptText, rag.topK());
                  if (context != null && !context.isBlank()) {
                    messages.addFirst(EngineMessage.system("RAG Context: \n" + context));
                  }
                });
        return options;
      }
    };
  }

  private static EngineTool engineTool(ToolCallback tool) {
    return new EngineTool(tool.getToolDefinition().name(), "", "", tool::call, tool);
  }

  private static List<ToolCallback> callbacks(List<EngineTool> tools) {
    return tools.stream().map(t -> (ToolCallback) t.nativeTool()).toList();
  }

  private static EngineChatOptions wrap(ChatOptions options) {
    return new EngineChatOptions(
        options.getModel(), options.getTemperature(), options.getMaxTokens(), options);
  }

  /** Creates a stub Tool interceptor that attaches demanded tools to chat options. */
  private PromptContextInterceptor createToolInterceptor(ToolRegistry toolRegistry) {
    return new PromptContextInterceptor() {
      @Override
      public EngineChatOptions preProcess(
          InternalChatRequest request,
          String promptText,
          List<EngineMessage> messages,
          EngineChatOptions options) {
        if (toolRegistry == null || toolRegistry.getRegisteredTools(ACTOR).isEmpty()) {
          return options;
        }

        String fullText =
            (promptText != null ? promptText : "")
                + " "
                + (request.prompt() != null ? request.prompt() : "");
        String lowerContext = fullText.toLowerCase();
        List<EngineTool> demanded = new java.util.ArrayList<>();
        for (EngineTool tool : toolRegistry.getRegisteredTools(ACTOR)) {
          String name = tool.name();
          boolean specialized = "analyzePoster".equals(name) || "analyzeAudioExtract".equals(name);
          if (!specialized
              || ("analyzePoster".equals(name)
                  && (lowerContext.contains("poster")
                      || lowerContext.contains("image")
                      || lowerContext.contains("visual")
                      || lowerContext.contains("picture")))
              || ("analyzeAudioExtract".equals(name)
                  && (lowerContext.contains("audio")
                      || lowerContext.contains("clip")
                      || lowerContext.contains("voice")
                      || lowerContext.contains("music")
                      || lowerContext.contains("sound")))) {
            demanded.add(tool);
          }
        }
        if (demanded.isEmpty()) {
          return options;
        }

        Object nativeOptions = options == null ? null : options.nativeOptions();
        if (nativeOptions instanceof OllamaChatOptions ollama) {
          return options.withNativeOptions(
              OllamaChatOptions.builder()
                  .model(ollama.getModel())
                  .temperature(ollama.getTemperature())
                  .numPredict(ollama.getNumPredict())
                  .numCtx(8192)
                  .toolCallbacks(callbacks(demanded))
                  .build());
        } else if (nativeOptions instanceof OpenAiChatOptions openai) {
          return options.withNativeOptions(
              OpenAiChatOptions.builder()
                  .model(openai.getModel())
                  .temperature(openai.getTemperature())
                  .maxTokens(openai.getMaxTokens())
                  .toolCallbacks(callbacks(demanded))
                  .build());
        }
        return options;
      }
    };
  }

  @Test
  void shouldGenerateSpeech() {
    // Given
    var req = new AudioRequest("hello tts", "alloy", "tts-1", Map.of(), Context.anonymous());
    byte[] expectedAudioBytes = new byte[] {1, 2, 3};
    when(speechModel.synthesize("hello tts", "tts-1", null)).thenReturn(expectedAudioBytes);

    // When
    byte[] audioBytes = engine.generateSpeech(req);

    // Then
    assertThat(audioBytes).isEqualTo(expectedAudioBytes);
  }

  @Test
  void shouldResolveCommercialModelWithApiKey() {
    // Given
    CoreProperties openAiProps =
        new CoreProperties(
            PROVIDER_OPENAI,
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);

    Engine openAiEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            openAiProps,
            List.of(),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    Context context = new Context("user-123", SESSION_1, Map.of(), Set.of());
    OpenAiChatOptions openAiOptions = OpenAiChatOptions.builder().model(MODEL_GPT4O).build();
    InternalChatRequest request =
        new InternalChatRequest(PROMPT_HELLO_CAP, null, wrap(openAiOptions), context);

    ChatModel commercialChatModel = mock(ChatModel.class);
    AssistantMessage assistantMessage = new AssistantMessage("Commercial response");
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

    when(credentialsProvider.getDecryptedApiKey("user-123", PROVIDER_OPENAI))
        .thenReturn(Optional.of(TEST_API_KEY));
    when(modelFactory.createChatModel(PROVIDER_OPENAI, MODEL_GPT4O, TEST_API_KEY))
        .thenReturn(commercialChatModel);
    when(commercialChatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    InternalChatResponse response = openAiEngine.chat(request);

    // Then
    assertThat(response.content()).isEqualTo("Commercial response");
    verify(modelFactory).createChatModel(PROVIDER_OPENAI, MODEL_GPT4O, TEST_API_KEY);
    verify(commercialChatModel).call(any(Prompt.class));
  }

  @Test
  void shouldWarnWhenApiKeyMissingForCommercialModel() {
    // Given
    CoreProperties openAiProps =
        new CoreProperties(
            PROVIDER_OPENAI,
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);

    Engine openAiEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            openAiProps,
            List.of(),
            null,
            eventPublisher,
            modelCatalogProvider,
            pipelineBridge,
            streamBridge,
            credentialsProvider);

    Context context = new Context("user-123", SESSION_1, Map.of(), Set.of());
    InternalChatRequest request = new InternalChatRequest(PROMPT_HELLO_CAP, null, null, context);

    AssistantMessage assistantMessage = new AssistantMessage("Local fallback response");
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

    when(credentialsProvider.getDecryptedApiKey("user-123", PROVIDER_OPENAI))
        .thenReturn(Optional.empty());
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    InternalChatResponse response = openAiEngine.chat(request);

    // Then
    assertThat(response.content()).isEqualTo("Local fallback response");
    verify(chatModel).call(any(Prompt.class));
    verifyNoInteractions(modelFactory);
  }

  @Test
  void shouldTriggerSimulatedBackpressure() {
    // Given
    InternalChatRequest request = InternalChatRequest.simple("Trigger backpressure scenario");
    AssistantMessage assistantMessage = new AssistantMessage("Under pressure response");
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

    // When
    InternalChatResponse response = engine.chat(request);

    // Then
    assertThat(response.content()).isEqualTo("Under pressure response");
  }

  @Test
  void shouldCloseAutoCloseableMcpClients() throws Exception {
    // Given
    InternalChatRequest request = InternalChatRequest.simple(PROMPT_HELLO_CAP);
    EnginePipelineBridge mockBridge = mock(EnginePipelineBridge.class);
    AutoCloseable mockCloseable1 = mock(AutoCloseable.class);
    AutoCloseable mockCloseable2 = mock(AutoCloseable.class);
    doThrow(new RuntimeException("Close failed")).when(mockCloseable2).close();

    var compiledCtx =
        new com.krizaka.sovkora.core.application.pipeline.EnginePipelineContext(
            PROVIDER_OLLAMA,
            SESSION_1,
            PROMPT_HELLO_CAP,
            new com.krizaka.sovkora.core.application.SpringPreparedPrompt(
                new Prompt(PROMPT_HELLO_CAP)),
            List.of(mockCloseable1, mockCloseable2));

    when(mockBridge.compileContext(any(), any(), any(), any())).thenReturn(compiledCtx);

    Engine customEngine =
        new Engine(
            ChatModelInvocation.of(chatModel, modelFactory),
            speechModel,
            properties,
            List.of(),
            null,
            eventPublisher,
            modelCatalogProvider,
            mockBridge,
            streamBridge,
            credentialsProvider);

    // When
    customEngine.chat(request);

    // Then
    verify(mockCloseable1).close();
    verify(mockCloseable2).close();
  }

  @Test
  void shouldReturnEmptyStringWhenResponseOrResultIsNull() {
    // Given
    InternalChatRequest request = InternalChatRequest.simple(PROMPT_HELLO_CAP);
    when(chatModel.call(any(Prompt.class))).thenReturn(null);

    // When
    InternalChatResponse response = engine.chat(request);

    // Then
    assertThat(response.content()).isEmpty();
  }
}
