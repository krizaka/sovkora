package com.krizaka.sovkora.adapter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.sovkora.adapter.springai.tool.ClientToolCallback;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.model.chat.CacheStrategy;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.chat.ToolRoundsExceededException;
import com.krizaka.sovkora.core.domain.model.chat.TurnSensitivity;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptCachePolicy;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolLoopBounds;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

/** The Spring AI side of the chat invocation port: route, text and usage. */
class SpringAiChatInvocationTest {

  private final ChatModel platform = mock(ChatModel.class);

  @SuppressWarnings("unchecked")
  private final DynamicChatModelFactory<ChatModel> factory = mock(DynamicChatModelFactory.class);

  private final SpringAiChatInvocation invocation = new SpringAiChatInvocation(platform, factory);
  private final PreparedPrompt prompt =
      new SpringPreparedPrompt(new Prompt(List.of(new UserMessage("hi"))));

  private static ChatResponse answer(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @Test
  void aPlatformRouteCallsThePlatformModel() {
    when(platform.call(any(Prompt.class))).thenReturn(answer("hello"));

    var out = invocation.call(prompt, ChatRoute.platform());

    assertThat(out.text()).isEqualTo("hello");
  }

  @Test
  void aUserKeyRouteBuildsTheModelOfThatKey() {
    var own = mock(ChatModel.class);
    when(factory.createChatModel("openai", null, "k")).thenReturn(own);
    when(own.call(any(Prompt.class))).thenReturn(answer("mine"));

    assertThat(invocation.call(prompt, ChatRoute.userKey("openai", "k")).text()).isEqualTo("mine");
    verify(factory).createChatModel("openai", null, "k");
  }

  @Test
  void aChunkWithoutTextIsEmptyWhenStreaming() {
    when(platform.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of())));

    var chunks = invocation.stream(prompt, ChatRoute.platform()).collectList().block();

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().text()).isEmpty();
  }

  @Test
  void aPromptOfAnotherKindIsRefused() {
    var foreign = mock(PreparedPrompt.class);
    assertThatThrownBy(() -> invocation.call(foreign, ChatRoute.platform()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- ADR-094 §4.1 (sovkora-engine#169): Spring AI 2 removed the tool loop from the model
  // ----

  /** A server tool that counts its runs and answers with a fixed result. */
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

  private static ChatResponse asking(String tool) {
    var call = new AssistantMessage.ToolCall("c1", "function", tool, "{}");
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
  }

  private static PreparedPrompt withTools(ToolCallback... tools) {
    var options = ToolCallingChatOptions.builder().toolCallbacks(List.of(tools)).build();
    return new SpringPreparedPrompt(new Prompt(List.of(new UserMessage("what is it?")), options));
  }

  @Test
  void aServerToolTheModelAsksForIsRunOnceAndItsResultReachesTheModel() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"), answer("it is 42"));

    var out = invocation.call(withTools(lookup), ChatRoute.platform());

    assertThat(out.text()).isEqualTo("it is 42");
    assertThat(out.toolCalls()).isEmpty();
    assertThat(lookup.runs).hasValue(1);
  }

  @Test
  void aToolRunByTheEngineIsReportedOnTheCompletion() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"), answer("it is 42"));

    var out = invocation.call(withTools(lookup), ChatRoute.platform());

    assertThat(out.executed()).extracting(ToolCall::name).containsExactly("lookup");
  }

  @Test
  void aToolRunByTheEngineIsReportedWhenStreamingToo() {
    var lookup = new Lookup();
    when(platform.stream(any(Prompt.class)))
        .thenReturn(Flux.just(asking("lookup")), Flux.just(answer("it is 42")));

    var chunks = invocation.stream(withTools(lookup), ChatRoute.platform()).collectList().block();

    assertThat(chunks)
        .flatExtracting(ChatCompletion::executed)
        .extracting(ToolCall::name)
        .containsExactly("lookup");
  }

  @Test
  void aServerToolTheModelAsksForIsRunWhenStreamingToo() {
    var lookup = new Lookup();
    when(platform.stream(any(Prompt.class)))
        .thenReturn(Flux.just(asking("lookup")), Flux.just(answer("it is 42")));

    var chunks = invocation.stream(withTools(lookup), ChatRoute.platform()).collectList().block();

    assertThat(chunks).extracting(ChatCompletion::text).contains("it is 42");
    assertThat(chunks).allSatisfy(c -> assertThat(c.toolCalls()).isEmpty());
    assertThat(lookup.runs).hasValue(1);
  }

  @Test
  void aClientToolIsHandedBackAndNeverRun() {
    var client = new ClientToolCallback(new ClientTool("pick", "d", "{\"type\":\"object\"}"));
    when(platform.call(any(Prompt.class))).thenReturn(asking("pick"));

    var out = invocation.call(withTools(client), ChatRoute.platform());

    assertThat(out.toolCalls()).extracting(ToolCall::name).containsExactly("pick");
  }

  @Test
  void aModelThatKeepsAskingStopsAtTheBound() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"));

    assertThatThrownBy(() -> invocation.call(withTools(lookup), ChatRoute.platform()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tool rounds");
    assertThat(lookup.runs.get()).isEqualTo(SpringAiChatInvocation.DEFAULT_MAX_TOOL_ROUNDS);
  }

  // ---- the bound is data per feature key (sovkora-engine#169) ----

  private SpringAiChatInvocation boundedBy(ToolLoopBounds bounds) {
    return new SpringAiChatInvocation(
        platform, factory, ToolCallingManager.builder().build(), bounds);
  }

  private static PreparedPrompt forFeature(String key, ToolCallback tool) {
    return withTools(tool).forFeature(key);
  }

  @Test
  void aFeatureBoundedAtOneRefusesTheSecondRoundWithATypedReason() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"));
    var bounded = boundedBy(key -> "agent.code.issue-to-pr".equals(key) ? 1 : 8);

    assertThatThrownBy(
            () -> bounded.call(forFeature("agent.code.issue-to-pr", lookup), ChatRoute.platform()))
        .isInstanceOfSatisfying(
            ToolRoundsExceededException.class,
            e -> {
              assertThat(e.featureKey()).isEqualTo("agent.code.issue-to-pr");
              assertThat(e.maxRounds()).isEqualTo(1);
            });
    assertThat(lookup.runs.get()).isEqualTo(1);
  }

  @Test
  void theStreamedLoopStopsAtTheSameBoundWithTheSameReason() {
    var lookup = new Lookup();
    when(platform.stream(any(Prompt.class))).thenReturn(Flux.just(asking("lookup")));
    var bounded = boundedBy(key -> 1);

    assertThatThrownBy(
            () ->
                bounded.stream(forFeature("agent.code.issue-to-pr", lookup), ChatRoute.platform())
                    .collectList()
                    .block())
        .isInstanceOf(ToolRoundsExceededException.class)
        .hasMessageContaining("1 tool rounds");
    assertThat(lookup.runs.get()).isEqualTo(1);
  }

  @Test
  void aTurnWithoutAFeatureKeyAsksTheBoundForTheDefault() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"));
    var asked = new java.util.concurrent.atomic.AtomicReference<String>("unset");
    var bounded =
        boundedBy(
            key -> {
              asked.set(key);
              return 2;
            });

    assertThatThrownBy(() -> bounded.call(withTools(lookup), ChatRoute.platform()))
        .isInstanceOf(ToolRoundsExceededException.class);
    assertThat(asked.get()).isNull();
    assertThat(lookup.runs.get()).isEqualTo(2);
  }

  @Test
  void theCachedTokensSpringAiReportsAreCarriedAndAddedUpOverRounds() {
    var usage =
        new org.springframework.ai.chat.metadata.DefaultUsage(1000, 50, 1050, null, 800L, 120L);
    var metadata =
        org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(usage).build();
    when(platform.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("cached"))), metadata));

    var out = invocation.call(prompt, ChatRoute.platform());

    assertThat(out.usage().cachedReadTokens()).isEqualTo(800);
    assertThat(out.usage().cacheWriteTokens()).isEqualTo(120);
    assertThat(out.usage().totalTokens()).isEqualTo(1050);
  }

  // ---- the prompt cache is a policy per feature key, and never on a SENSITIVE turn (#173) ----

  private static Prompt toolsPrompt() {
    return new Prompt(
        List.of(new UserMessage("hi")),
        org.springframework.ai.model.tool.ToolCallingChatOptions.builder().model("claude").build());
  }

  private static org.springframework.ai.anthropic.AnthropicCacheStrategy cacheSentOf(
      org.springframework.ai.anthropic.AnthropicChatModel anthropic,
      PromptCachePolicy policy,
      PreparedPrompt prepared) {
    when(anthropic.call(any(Prompt.class))).thenReturn(answer("ok"));
    new SpringAiChatInvocation(
            anthropic,
            mock(DynamicChatModelFactory.class),
            org.springframework.ai.model.tool.ToolCallingManager.builder().build(),
            ToolLoopBounds.fixed(3),
            io.micrometer.observation.ObservationRegistry.NOOP,
            policy)
        .call(prepared, ChatRoute.platform());
    var sent = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(anthropic).call(sent.capture());
    return sent.getValue().getOptions()
            instanceof org.springframework.ai.anthropic.AnthropicChatOptions options
        ? options.getCacheOptions().getStrategy()
        : null;
  }

  @Test
  void anAnthropicTurnIsCachedAsItsFeaturesPolicySays() {
    var anthropic = mock(org.springframework.ai.anthropic.AnthropicChatModel.class);
    PreparedPrompt prepared = new SpringPreparedPrompt(toolsPrompt(), "chat.support");

    var sent =
        cacheSentOf(
            anthropic,
            key -> "chat.support".equals(key) ? CacheStrategy.SYSTEM_ONLY : CacheStrategy.NONE,
            prepared);

    assertThat(sent).isEqualTo(org.springframework.ai.anthropic.AnthropicCacheStrategy.SYSTEM_ONLY);
  }

  @Test
  void aFeatureWhosePolicyIsNoneIsNeverCached() {
    var anthropic = mock(org.springframework.ai.anthropic.AnthropicChatModel.class);

    var sent =
        cacheSentOf(
            anthropic,
            PromptCachePolicy.fixed(CacheStrategy.NONE),
            new SpringPreparedPrompt(toolsPrompt(), "chat.support"));

    assertThat(sent).isNull();
  }

  @Test
  void aSensitiveTurnIsNeverSentWithCacheOptionsWhateverThePolicySays() {
    var anthropic = mock(org.springframework.ai.anthropic.AnthropicChatModel.class);

    var sent =
        cacheSentOf(
            anthropic,
            PromptCachePolicy.fixed(CacheStrategy.SYSTEM_AND_TOOLS),
            new SpringPreparedPrompt(toolsPrompt(), "chat.support").asSensitive());

    assertThat(sent).isNull();
  }

  @Test
  void aTurnWithoutADeclaredControlIsNotSensitiveButOneWithAScopeGuardIs() {
    assertThat(TurnSensitivity.of(java.util.Map.of())).isFalse();
    assertThat(TurnSensitivity.of(java.util.Map.of(TurnSensitivity.scopeKey(), "a,b"))).isTrue();
    assertThat(TurnSensitivity.of(java.util.Map.of(TurnSensitivity.safetyKey(), "x"))).isTrue();
    assertThat(TurnSensitivity.of(null)).isFalse();
  }
}
