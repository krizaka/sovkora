package com.krizaka.sovkora.adapter.springai.prompt;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolResolution;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.*;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider.PlatformMcpServer;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider.UserMcpServer;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.MimeTypeUtils;

@ExtendWith(MockitoExtension.class)
class EnginePipelineBridgeTest {

  @Mock private ModelCatalogProvider modelCatalogProvider;
  @Mock private ChatModel chatModel;
  @Mock private PlatformMcpServerProvider platformMcpServerProvider;
  @Mock private UserMcpServerProvider userMcpServerProvider;
  @Mock private ToolRegistry toolRegistry;
  private EnginePipelineBridge enginePipelineBridge;

  @BeforeEach
  void setUp() {
    enginePipelineBridge =
        new EnginePipelineBridge(
            modelCatalogProvider,
            new SpringAiToolResolution(
                platformMcpServerProvider, userMcpServerProvider, toolRegistry),
            new SpringAiPromptAssembly());
  }

  @Nested
  @DisplayName("mapMessage()")
  class MapMessage {

    @Test
    @DisplayName("maps 'system' role to SystemMessage")
    void mapsSystem() {
      Message result =
          MessageCompiler.mapMessage(new InternalChatRequest.ChatMessage("system", "hello"));
      assertInstanceOf(SystemMessage.class, result);
      assertEquals("hello", result.getText());
    }

    @Test
    @DisplayName("maps 'assistant' role to AssistantMessage")
    void mapsAssistant() {
      Message result =
          MessageCompiler.mapMessage(new InternalChatRequest.ChatMessage("assistant", "response"));
      assertInstanceOf(AssistantMessage.class, result);
    }

    @Test
    @DisplayName("maps 'user' role to UserMessage")
    void mapsUser() {
      Message result =
          MessageCompiler.mapMessage(new InternalChatRequest.ChatMessage("user", "question"));
      assertInstanceOf(UserMessage.class, result);
    }

    @Test
    @DisplayName("maps unknown role to UserMessage (default)")
    void mapsUnknown() {
      Message result =
          MessageCompiler.mapMessage(new InternalChatRequest.ChatMessage("custom", "data"));
      assertInstanceOf(UserMessage.class, result);
    }
  }

  @Nested
  @DisplayName("compileContext()")
  class CompileContext {

    @BeforeEach
    void setUpActiveModel() {
      lenient()
          .when(modelCatalogProvider.getActiveChatModel())
          .thenReturn(Optional.of("llama3.1:8b"));
    }

    @Test
    @DisplayName("compiles context without pipeline (null pipeline)")
    void compilesWithoutPipeline() {
      var request = InternalChatRequest.simple("test prompt");
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertEquals(PROVIDER_OLLAMA, result.provider());
      assertEquals("test prompt", result.promptText());
      assertNotNull(result.toPrompt());
    }

    @Test
    @DisplayName("compiles context with null prompt text (fallback to empty string)")
    void compilesWithNullPrompt() {
      var request = new InternalChatRequest(null, List.of(), null, Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertEquals("", result.promptText());
    }

    @Test
    @DisplayName("compiles context with active pipeline resolving prompt context")
    void compilesWithActivePipeline() {
      var pipeline = mock(DynamicPipelineExecutor.class);
      var ctx = new Context(USER_1, "conv-123", Map.of(), Set.of());
      var promptCtx =
          new PromptContext("refined question", Map.of()).withRoutedProvider(PROVIDER_OLLAMA);
      when(pipeline.process("test prompt", 0, ctx)).thenReturn(promptCtx);

      var request = new InternalChatRequest("test prompt", List.of(), null, ctx);
      var result =
          enginePipelineBridge.compileContext(request, pipeline, "fallbackProvider", List.of());

      assertEquals("refined question", result.promptText());
      assertEquals(PROVIDER_OLLAMA, result.provider());
    }

    @Test
    @DisplayName("[ADR-063] a prompt carrying media goes through the pipeline, declared as media")
    void aMediaPromptGoesThroughThePipelineWithItsMediaDeclared() {
      var pipeline = mock(DynamicPipelineExecutor.class);
      var ctx = new Context(USER_1, "conv-123", Map.of(), Set.of());
      when(pipeline.process(anyString(), anyInt(), any()))
          .thenReturn(new PromptContext("Analyze please", Map.of()));
      var request =
          new InternalChatRequest("Analyze [posterBase64: AQIDBAU=] please", List.of(), null, ctx);

      enginePipelineBridge.compileContext(request, pipeline, PROVIDER_OLLAMA, List.of());

      // The text without the base64, and the fact that an image travels with it: a pipeline told
      // only the text would judge "Analyze please" and pass a turn whose subject is the image.
      verify(pipeline).process("Analyze please", 1, ctx);
    }

    @Test
    @DisplayName("[ADR-063] a text prompt declares no media")
    void aTextPromptDeclaresNoMedia() {
      var pipeline = mock(DynamicPipelineExecutor.class);
      var ctx = new Context(USER_1, "conv-123", Map.of(), Set.of());
      when(pipeline.process(anyString(), anyInt(), any()))
          .thenReturn(new PromptContext("hello", Map.of()));

      enginePipelineBridge.compileContext(
          new InternalChatRequest("hello", List.of(), null, ctx),
          pipeline,
          PROVIDER_OLLAMA,
          List.of());

      verify(pipeline).process("hello", 0, ctx);
    }

    @Test
    @DisplayName(
        "[ADR-062] a disabled switch refuses a media prompt: the pipeline's refusal stops it")
    void disabledSwitchRefusesAMediaPrompt() {
      var pipeline = mock(DynamicPipelineExecutor.class);
      when(pipeline.process(anyString(), anyInt(), any()))
          .thenThrow(new PipelineDisabledException());
      var request =
          new InternalChatRequest(
              "Analyze [posterBase64: AQIDBAU=] please", List.of(), null, Context.anonymous());

      assertThrows(
          PipelineDisabledException.class,
          () -> enginePipelineBridge.compileContext(request, pipeline, PROVIDER_OLLAMA, List.of()));
    }

    @Test
    @DisplayName("provider resolves to ollama when options is OllamaChatOptions")
    void resolvesOllamaFromOptions() {
      var ollamaOptions = OllamaChatOptions.builder().build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(ollamaOptions), Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, null, List.of());

      assertEquals(PROVIDER_OLLAMA, result.provider());
    }

    @Test
    @DisplayName("provider resolves to openai when options is OpenAiChatOptions")
    void resolvesOpenAiFromOpenAiOptions() {
      var openAiOptions = OpenAiChatOptions.builder().build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(openAiOptions), Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, null, List.of());

      assertEquals(PROVIDER_OPENAI, result.provider());
    }

    @Test
    @DisplayName("provider resolves to defaultProvider when other options type is present")
    void resolvesDefaultFromOtherOptions() {
      var customOptions = ChatOptions.builder().build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(customOptions), Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, "my-default", List.of());

      assertEquals("my-default", result.provider());
    }

    @Test
    @DisplayName("openai provider string is kept as openai")
    void keepsOpenAiProvider() {
      var request = InternalChatRequest.simple(TEST);
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OPENAI, List.of());

      assertEquals(PROVIDER_OPENAI, result.provider());
    }

    @Test
    @DisplayName("throws CoreException when provider is null")
    void throwsOnNullProvider() {
      var request = InternalChatRequest.simple(TEST);
      var emptyInterceptors = List.<PromptContextInterceptor>of();
      assertThrows(
          CoreException.class,
          () -> enginePipelineBridge.compileContext(request, null, null, emptyInterceptors));
    }

    @Test
    @DisplayName("throws CoreException when provider is blank")
    void throwsOnBlankProvider() {
      var request = InternalChatRequest.simple(TEST);
      var emptyInterceptors = List.<PromptContextInterceptor>of();
      assertThrows(
          CoreException.class,
          () -> enginePipelineBridge.compileContext(request, null, "  ", emptyInterceptors));
    }

    @Test
    @DisplayName("throws CoreException when Ollama active model is missing")
    void throwsOnMissingOllamaModel() {
      when(modelCatalogProvider.getActiveChatModel()).thenReturn(Optional.empty());
      var request = InternalChatRequest.simple(TEST);
      var emptyInterceptors = List.<PromptContextInterceptor>of();
      assertThrows(
          CoreException.class,
          () ->
              enginePipelineBridge.compileContext(
                  request, null, PROVIDER_OLLAMA, emptyInterceptors));
    }

    @Test
    @DisplayName("maps message history correctly from request")
    void mapsMessageHistory() {
      var messages =
          List.of(
              new InternalChatRequest.ChatMessage("system", "sys-msg"),
              new InternalChatRequest.ChatMessage("assistant", "assistant-msg"),
              new InternalChatRequest.ChatMessage("user", "user-msg"));
      var request = new InternalChatRequest(TEST, messages, null, Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertEquals(4, ((SpringPreparedPrompt) result.toPrompt()).prompt().getInstructions().size());
      assertEquals(
          "sys-msg",
          ((SpringPreparedPrompt) result.toPrompt())
              .prompt()
              .getInstructions()
              .getFirst()
              .getText());
    }

    @Test
    @DisplayName("maps OpenAiChatOptions to OllamaChatOptions correctly")
    void mapsOpenAiChatOptionsToOllama() {
      var openAiOptions = OpenAiChatOptions.builder().model("gpt-4").temperature(0.3).build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(openAiOptions), Context.anonymous());

      var pipeline = mock(DynamicPipelineExecutor.class);
      var promptCtx = new PromptContext(TEST, Map.of()).withRoutedProvider(PROVIDER_OLLAMA);
      when(pipeline.process(anyString(), anyInt(), any())).thenReturn(promptCtx);

      var result =
          enginePipelineBridge.compileContext(request, pipeline, PROVIDER_OLLAMA, List.of());

      assertInstanceOf(
          OllamaChatOptions.class,
          ((SpringPreparedPrompt) result.toPrompt()).prompt().getOptions());
      var options =
          (OllamaChatOptions) ((SpringPreparedPrompt) result.toPrompt()).prompt().getOptions();
      assertEquals("llama3.1:8b", options.getModel());
      assertEquals(0.3, options.getTemperature());
    }

    @Test
    @DisplayName("maps OpenAiChatOptions with non-gpt model cleanly")
    void mapsOpenAiChatOptionsNonGptModel() {
      var openAiOptions =
          OpenAiChatOptions.builder().model("custom-model").temperature(0.5).build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(openAiOptions), Context.anonymous());

      var pipeline = mock(DynamicPipelineExecutor.class);
      var promptCtx = new PromptContext(TEST, Map.of()).withRoutedProvider(PROVIDER_OLLAMA);
      when(pipeline.process(anyString(), anyInt(), any())).thenReturn(promptCtx);

      var result =
          enginePipelineBridge.compileContext(request, pipeline, PROVIDER_OLLAMA, List.of());

      var options =
          (OllamaChatOptions) ((SpringPreparedPrompt) result.toPrompt()).prompt().getOptions();
      assertEquals("custom-model", options.getModel());
    }

    @Test
    @DisplayName("sets default options based on provider when options is null")
    void defaultOptionsPerProvider() {
      // Test ollama
      var request1 = InternalChatRequest.simple(TEST);
      var result1 = enginePipelineBridge.compileContext(request1, null, PROVIDER_OLLAMA, List.of());
      var opt1 =
          (OllamaChatOptions) ((SpringPreparedPrompt) result1.toPrompt()).prompt().getOptions();
      assertEquals("llama3.1:8b", opt1.getModel());
      assertEquals(0.7, opt1.getTemperature());

      // Test openai (returns OpenAI default options)
      var result2 = enginePipelineBridge.compileContext(request1, null, PROVIDER_OPENAI, List.of());
      var opt2 =
          (OpenAiChatOptions) ((SpringPreparedPrompt) result2.toPrompt()).prompt().getOptions();
      assertNotNull(opt2);
      assertEquals(0.7, opt2.getTemperature());

      // Test custom default
      var result3 = enginePipelineBridge.compileContext(request1, null, "custom", List.of());
      var opt3 = ((SpringPreparedPrompt) result3.toPrompt()).prompt().getOptions();
      assertInstanceOf(DefaultChatOptions.class, opt3);
      assertEquals(0.7, opt3.getTemperature());
    }

    @Test
    @DisplayName("corrects model name in OllamaChatOptions when model is null/blank")
    void correctsModelInOllamaOptions() {
      var ollamaOptions = OllamaChatOptions.builder().model("").temperature(0.9).build();
      var request =
          new InternalChatRequest(TEST, List.of(), wrap(ollamaOptions), Context.anonymous());
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      var options =
          (OllamaChatOptions) ((SpringPreparedPrompt) result.toPrompt()).prompt().getOptions();
      assertEquals("llama3.1:8b", options.getModel());
      assertEquals(0.9, options.getTemperature());
    }

    @Test
    @DisplayName("applies registered interceptors to options")
    void appliesInterceptors() {
      var request = InternalChatRequest.simple(TEST);
      var mockInterceptor = mock(PromptContextInterceptor.class);
      var mockOptions = mock(ChatOptions.class);
      when(mockInterceptor.preProcess(
              eq(request), eq(TEST), anyList(), any(EngineChatOptions.class)))
          .thenReturn(new EngineChatOptions(null, null, null, mockOptions));

      var result =
          enginePipelineBridge.compileContext(
              request, null, PROVIDER_OLLAMA, List.of(mockInterceptor));
      assertEquals(mockOptions, ((SpringPreparedPrompt) result.toPrompt()).prompt().getOptions());
    }

    @Test
    @DisplayName("compiles context with platform and user MCP servers")
    void compilesWithMcpServers() {
      var request =
          new InternalChatRequest(
              "test prompt", List.of(), null, new Context(USER_1, "conv-123", Map.of(), Set.of()));

      var platformServer =
          new PlatformMcpServer(
              1, "platform-label", "SSE", "http://platform/mcp", null, null, "plat-token", true);
      var platformLocalServer =
          new PlatformMcpServer(
              2, "platform-local", "LOCAL", null, "echo", "hello,world", null, true);
      var userServer =
          new UserMcpServer(1, USER_1, "user-label", "http://user/mcp", "user-token", true);

      when(platformMcpServerProvider.getActivePlatformMcpServers())
          .thenReturn(List.of(platformServer, platformLocalServer));
      when(userMcpServerProvider.getActiveUserMcpServers(USER_1)).thenReturn(List.of(userServer));

      var mockSyncClient = mock(McpSyncClient.class);
      var mockBuilder = mock(McpClient.SyncSpec.class);
      when(mockBuilder.build()).thenReturn(mockSyncClient);

      var mockToolCallback = mock(ToolCallback.class);

      try (var mcMock = mockStatic(McpClient.class);
          var syncMock = mockStatic(SyncMcpToolCallbackProvider.class)) {
        stubMcpClientSync(mcMock, mockBuilder);
        stubSyncToolCallbacks(syncMock, List.of(mockToolCallback));

        var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

        assertNotNull(result);
        assertEquals(3, result.closeables().size()); // 2 platform + 1 user
      }
    }

    @Test
    @DisplayName("handles MCP initialization failure gracefully")
    void handlesMcpFailure() {
      var request =
          new InternalChatRequest(
              "test prompt", List.of(), null, new Context(USER_1, "conv-123", Map.of(), Set.of()));
      var platformServer =
          new PlatformMcpServer(
              1, "platform-label", "SSE", "http://platform/mcp", null, null, "plat-token", true);
      when(platformMcpServerProvider.getActivePlatformMcpServers())
          .thenReturn(List.of(platformServer));

      try (var mcMock = mockStatic(McpClient.class)) {
        stubMcpClientSyncThrows(mcMock, new RuntimeException("Injected fail"));

        var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());
        assertNotNull(result);
        assertTrue(result.closeables().isEmpty());
      }
    }

    @Test
    @DisplayName("an anonymous context gets no MCP server at all: no actor, no tools (ADR-091)")
    void anonymousContextGetsNoMcpServers() {
      var request = new InternalChatRequest("test prompt", List.of(), null, Context.anonymous());

      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertNotNull(result);
      assertEquals(0, result.closeables().size());
      verifyNoInteractions(userMcpServerProvider);
      verifyNoInteractions(platformMcpServerProvider);
    }

    @Test
    @DisplayName("extracts posterBase64 and creates image Media object")
    void extractsPosterBase64() {
      var request = InternalChatRequest.simple("test prompt [posterBase64: dGVzdA==]");
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertEquals("test prompt", result.promptText());
      var userMsg =
          ((SpringPreparedPrompt) result.toPrompt()).prompt().getInstructions().getFirst();
      assertInstanceOf(UserMessage.class, userMsg);
      var mediaList = ((UserMessage) userMsg).getMedia();
      assertEquals(1, mediaList.size());
      assertEquals(MimeTypeUtils.IMAGE_PNG, mediaList.getFirst().getMimeType());
    }

    @Test
    @DisplayName("[ADR-063] a marker in the refined prompt is text, never media")
    void aMarkerInTheRefinedPromptIsNotAttachedAsMedia() {
      var request = new InternalChatRequest("fuzzy", List.of(), null, Context.anonymous());
      var pipeline = mock(DynamicPipelineExecutor.class);
      var promptCtx = new PromptContext("refined [posterBase64: dGVzdA==]", Map.of());
      when(pipeline.process(anyString(), anyInt(), any())).thenReturn(promptCtx);

      var result =
          enginePipelineBridge.compileContext(request, pipeline, PROVIDER_OLLAMA, List.of());

      // The pipeline was told this turn carried no media. An image lifted out of its output would
      // reach the model behind every guard that passed the turn as text.
      var userMsg =
          ((SpringPreparedPrompt) result.toPrompt()).prompt().getInstructions().getFirst();
      assertInstanceOf(UserMessage.class, userMsg);
      assertTrue(((UserMessage) userMsg).getMedia().isEmpty());
    }

    @Test
    @DisplayName("falls back to standard message if base64 decoding fails")
    void fallbackOnBase64DecodeFailure() {
      var request = InternalChatRequest.simple("test prompt [posterBase64: invalid-base64!!!]");
      var result = enginePipelineBridge.compileContext(request, null, PROVIDER_OLLAMA, List.of());

      assertEquals("test prompt", result.promptText());
      var userMsg =
          ((SpringPreparedPrompt) result.toPrompt()).prompt().getInstructions().getFirst();
      assertInstanceOf(UserMessage.class, userMsg);
      assertTrue(((UserMessage) userMsg).getMedia().isEmpty());
    }

    @Test
    @DisplayName("resolves provider for GoogleGenAiChatOptions and AnthropicChatOptions")
    void resolvesGeminiAndAnthropicFromOptions() {
      var geminiOptions = GoogleGenAiChatOptions.builder().build();
      var req1 = new InternalChatRequest(TEST, List.of(), wrap(geminiOptions), Context.anonymous());
      assertEquals(
          "gemini", enginePipelineBridge.compileContext(req1, null, null, List.of()).provider());

      var anthropicOptions = AnthropicChatOptions.builder().build();
      var req2 =
          new InternalChatRequest(TEST, List.of(), wrap(anthropicOptions), Context.anonymous());
      assertEquals(
          "anthropic", enginePipelineBridge.compileContext(req2, null, null, List.of()).provider());
    }

    @Test
    @DisplayName("builds default options for other providers when options is null")
    void buildsDefaultOptionsForOtherProviders() {
      var request = InternalChatRequest.simple(TEST);

      var res1 = enginePipelineBridge.compileContext(request, null, "anthropic", List.of());
      assertInstanceOf(
          AnthropicChatOptions.class,
          ((SpringPreparedPrompt) res1.toPrompt()).prompt().getOptions());

      var res2 = enginePipelineBridge.compileContext(request, null, "gemini", List.of());
      assertInstanceOf(
          GoogleGenAiChatOptions.class,
          ((SpringPreparedPrompt) res2.toPrompt()).prompt().getOptions());
    }
  }

  @Nested
  @DisplayName("removeTools()")
  class RemoveTools {

    @Test
    @DisplayName("returns same prompt when options is null")
    void returnsPromptWhenNoOptions() {
      var prompt = new org.springframework.ai.chat.prompt.Prompt(TEST);
      var result = SpringPreparedPrompt.removeTools(prompt);
      assertSame(prompt, result);
    }

    @Test
    @DisplayName("removes tools from OllamaChatOptions")
    void removesFromOllama() {
      var options =
          OllamaChatOptions.builder().model("model-x").temperature(0.5).numPredict(100).build();
      var prompt = new org.springframework.ai.chat.prompt.Prompt(TEST, options);
      var result = SpringPreparedPrompt.removeTools(prompt);

      var cleanOptions = (OllamaChatOptions) result.getOptions();
      assertEquals("model-x", cleanOptions.getModel());
      assertEquals(0.5, cleanOptions.getTemperature());
      assertEquals(100, cleanOptions.getNumPredict());
    }

    @Test
    @DisplayName("removes tools from OpenAiChatOptions")
    void removesFromOpenAi() {
      var options =
          OpenAiChatOptions.builder().model("model-y").temperature(0.6).maxTokens(200).build();
      var prompt = new org.springframework.ai.chat.prompt.Prompt(TEST, options);
      var result = SpringPreparedPrompt.removeTools(prompt);

      var cleanOptions = (OpenAiChatOptions) result.getOptions();
      assertEquals("model-y", cleanOptions.getModel());
      assertEquals(0.6, cleanOptions.getTemperature());
      assertEquals(200, cleanOptions.getMaxTokens());
    }

    @Test
    @DisplayName("returns other options unchanged")
    void returnsOtherOptionsUnchanged() {
      var options = ChatOptions.builder().build();
      var prompt = new org.springframework.ai.chat.prompt.Prompt(TEST, options);
      var result = SpringPreparedPrompt.removeTools(prompt);
      assertSame(options, result.getOptions());
    }
  }

  // --- MockStatic helpers (S5778-compliant: single invocation per lambda) ---

  @SuppressWarnings("unchecked")
  private static void stubMcpClientSync(
      org.mockito.MockedStatic<McpClient> mcMock, McpClient.SyncSpec mockBuilder) {
    mcMock
        .when(() -> McpClient.sync(any(io.modelcontextprotocol.spec.McpClientTransport.class)))
        .thenReturn(mockBuilder);
  }

  @SuppressWarnings("unchecked")
  private static void stubMcpClientSyncThrows(
      org.mockito.MockedStatic<McpClient> mcMock, RuntimeException exception) {
    mcMock
        .when(() -> McpClient.sync(any(io.modelcontextprotocol.spec.McpClientTransport.class)))
        .thenThrow(exception);
  }

  private static void stubSyncToolCallbacks(
      org.mockito.MockedStatic<SyncMcpToolCallbackProvider> syncMock,
      List<ToolCallback> callbacks) {
    syncMock
        .when(() -> SyncMcpToolCallbackProvider.syncToolCallbacks(anyList()))
        .thenReturn(callbacks);
  }

  private static EngineChatOptions wrap(ChatOptions options) {
    return new EngineChatOptions(
        options.getModel(), options.getTemperature(), options.getMaxTokens(), options);
  }
}
