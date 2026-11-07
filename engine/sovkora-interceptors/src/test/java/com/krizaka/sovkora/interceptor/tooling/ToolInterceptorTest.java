package com.krizaka.sovkora.interceptor.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolOptions;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the interceptor chooses; how a provider's options carry the tools is the adapter's test. */
class ToolInterceptorTest {

  private final ToolOptions toolOptions = mock(ToolOptions.class);

  private static EngineTool tool(String name) {
    return new EngineTool(name, "", "{}", json -> "", null);
  }

  private static final Context EMPLOYEE_CONTEXT =
      new Context("emp-1", "c-1", Map.of(), Set.of(new Authority("ROLE_USER")));
  private static final Actor EMPLOYEE = Actor.from(EMPLOYEE_CONTEXT).orElseThrow();

  private static InternalChatRequest request(String prompt, boolean streaming) {
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(EMPLOYEE_CONTEXT);
    when(request.streaming()).thenReturn(streaming);
    when(request.prompt()).thenReturn(prompt);
    when(request.messages()).thenReturn(Collections.emptyList());
    return request;
  }

  private ToolInterceptor interceptorOver(EngineTool... tools) {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    when(toolRegistry.getRegisteredTools(EMPLOYEE)).thenReturn(List.of(tools));
    return new ToolInterceptor(toolRegistry, toolOptions);
  }

  @Test
  @DisplayName("preProcess offers the tools on a streamed turn too (the stream path runs them)")
  void preProcessStreaming() {
    EngineChatOptions options = new EngineChatOptions("m", 0.1, 10);
    EngineChatOptions withTools = new EngineChatOptions("m", 0.1, 10);
    EngineTool lookup = tool("lookup");
    when(toolOptions.withTools(options, List.of(lookup))).thenReturn(withTools);

    EngineChatOptions result =
        interceptorOver(lookup)
            .preProcess(request("prompt", true), "prompt", new ArrayList<>(), options);

    assertThat(result).isSameAs(withTools);
  }

  @Test
  @DisplayName("preProcess returns original options for a vision model")
  void preProcessVisionModel() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry, toolOptions);
    EngineChatOptions options = new EngineChatOptions("llama3.2-vision", null, null);

    EngineChatOptions result =
        interceptor.preProcess(request("prompt", false), "prompt", new ArrayList<>(), options);

    assertThat(result).isSameAs(options);
    verifyNoInteractions(toolRegistry, toolOptions);
  }

  @Test
  @DisplayName("preProcess returns original options when the registry is null or empty")
  void preProcessRegistryNullOrEmpty() {
    EngineChatOptions options = new EngineChatOptions("m", null, null);
    InternalChatRequest request = request("prompt", false);

    assertThat(
            new ToolInterceptor(null, toolOptions)
                .preProcess(request, "prompt", new ArrayList<>(), options))
        .isSameAs(options);
    assertThat(interceptorOver().preProcess(request, "prompt", new ArrayList<>(), options))
        .isSameAs(options);
    verifyNoInteractions(toolOptions);
  }

  @Test
  @DisplayName("preProcess returns original options when no tools are demanded by keywords")
  void preProcessNoDemandedTools() {
    ToolInterceptor interceptor = interceptorOver(tool("analyzePoster"));
    EngineChatOptions options = new EngineChatOptions("m", null, null);

    EngineChatOptions result =
        interceptor.preProcess(
            request("hello standard text", false), "just hello", new ArrayList<>(), options);

    assertThat(result).isSameAs(options);
    verifyNoInteractions(toolOptions);
  }

  @Test
  @DisplayName("preProcess offers the poster tool and the generic ones when the poster is demanded")
  void preProcessPosterDemanded() {
    EngineTool poster = tool("analyzePoster");
    EngineTool generic = tool("genericCalculator");
    EngineTool audio = tool("analyzeAudioExtract");
    EngineChatOptions options = new EngineChatOptions("llama3.2", 0.7, 128);
    EngineChatOptions withTools = options.withNativeOptions("tools attached");
    when(toolOptions.withTools(options, List.of(poster, generic))).thenReturn(withTools);

    EngineChatOptions result =
        interceptorOver(poster, generic, audio)
            .preProcess(request("show me the poster info", false), "", new ArrayList<>(), options);

    assertThat(result).isSameAs(withTools);
  }

  @Test
  @DisplayName("preProcess offers the audio tool when it is demanded in a user message")
  void preProcessAudioDemanded() {
    EngineTool audio = tool("analyzeAudioExtract");
    InternalChatRequest request = request("standard prompt", false);
    when(request.messages())
        .thenReturn(List.of(new InternalChatRequest.ChatMessage("user", "extract the audio?")));
    EngineChatOptions options = new EngineChatOptions("gpt-4o", 0.5, 100);
    EngineChatOptions withTools = options.withNativeOptions("tools attached");
    when(toolOptions.withTools(options, List.of(audio))).thenReturn(withTools);

    EngineChatOptions result =
        interceptorOver(audio).preProcess(request, null, new ArrayList<>(), options);

    assertThat(result).isSameAs(withTools);
  }

  @Test
  @DisplayName("a turn with no verified actor is offered no tool, whatever the registry holds")
  void noActorNoTools() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    when(toolRegistry.getRegisteredTools(null)).thenReturn(List.of());
    when(toolRegistry.getRegisteredTools(EMPLOYEE)).thenReturn(List.of(tool("genericCalculator")));
    InternalChatRequest request = request("compute this", false);
    when(request.context()).thenReturn(null);
    EngineChatOptions options = new EngineChatOptions("m", null, null);

    EngineChatOptions result =
        new ToolInterceptor(toolRegistry, toolOptions)
            .preProcess(request, "compute", new ArrayList<>(), options);

    assertThat(result).isSameAs(options);
    verifyNoInteractions(toolOptions);
  }
}
