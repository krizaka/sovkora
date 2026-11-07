package com.krizaka.sovkora.interceptor.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.adapter.springai.chat.SpringAiChatInvocation;
import com.krizaka.sovkora.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolOptions;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

/**
 * The tool loop end to end, in both modes (ADR-094 §4.1, sovkora-engine#169): the real {@link
 * ToolInterceptor} offers the actor's server tool, the real Spring AI adapter runs it and feeds the
 * result back. Sync and streamed chat must behave the same; the streamed path was skipped by the
 * interceptor and no test crossed the interceptor and the adapter together.
 */
class ToolLoopContractTest {

  private static final Context CONTEXT =
      new Context("emp-1", "c-1", Map.of(), Set.of(new Authority("ROLE_USER")));
  private static final Actor ACTOR = Actor.from(CONTEXT).orElseThrow();

  /** A server tool that counts its runs. */
  private static final class Lookup implements ToolCallback {
    final AtomicInteger runs = new AtomicInteger();

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder()
          .name("lookup")
          .description("d")
          .inputSchema("{\"type\":\"object\"}")
          .build();
    }

    @Override
    public String call(String toolInput) {
      runs.incrementAndGet();
      return "\"42\"";
    }
  }

  private static ChatResponse answer(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static ChatResponse asking(String tool) {
    var call = new AssistantMessage.ToolCall("c1", "function", tool, "{}");
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
  }

  /** What the engine does for one turn: interceptor, then the invocation port. */
  private static List<ChatCompletion> turn(boolean streaming, Lookup lookup) {
    ToolRegistry registry = mock(ToolRegistry.class);
    when(registry.getRegisteredTools(ACTOR))
        .thenReturn(List.of(new EngineTool("lookup", "d", "{}", json -> "", lookup)));
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(CONTEXT);
    when(request.streaming()).thenReturn(streaming);
    when(request.prompt()).thenReturn("what is it?");
    when(request.messages()).thenReturn(Collections.emptyList());

    EngineChatOptions options =
        new EngineChatOptions("m", 0.1, 10, OpenAiChatOptions.builder().model("m").build());
    EngineChatOptions offered =
        new ToolInterceptor(registry, new SpringAiToolOptions())
            .preProcess(request, "what is it?", new ArrayList<>(), options);
    var prompt =
        new SpringPreparedPrompt(
            new Prompt(
                List.of(new UserMessage("what is it?")),
                (org.springframework.ai.chat.prompt.ChatOptions) offered.nativeOptions()));

    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(asking("lookup"), answer("it is 42"));
    when(model.stream(any(Prompt.class)))
        .thenReturn(Flux.just(asking("lookup")), Flux.just(answer("it is 42")));
    @SuppressWarnings("unchecked")
    DynamicChatModelFactory<ChatModel> factory = mock(DynamicChatModelFactory.class);
    var invocation = new SpringAiChatInvocation(model, factory);
    return streaming
        ? invocation.stream(prompt, ChatRoute.platform()).collectList().block()
        : List.of(invocation.call(prompt, ChatRoute.platform()));
  }

  @ParameterizedTest(name = "streaming={0}")
  @ValueSource(booleans = {false, true})
  void aServerToolIsOfferedRunOnceAndItsResultFeedsTheAnswer(boolean streaming) {
    var lookup = new Lookup();

    var out = turn(streaming, lookup);

    assertThat(lookup.runs).hasValue(1);
    assertThat(out).extracting(ChatCompletion::text).contains("it is 42");
    assertThat(out).flatExtracting(ChatCompletion::toolCalls).isEmpty();
    assertThat(out)
        .flatExtracting(ChatCompletion::executed)
        .extracting(ToolCall::name)
        .containsExactly("lookup");
  }
}
