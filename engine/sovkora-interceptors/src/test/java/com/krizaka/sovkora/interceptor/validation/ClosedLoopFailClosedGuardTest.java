package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineContext;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Guard of sovkora-engine#125 (ADR-092, Loi 25 art. 12.1): the control loop fails closed. A
 * streamed answer is screened before the stream ends, and a judge outage never yields a served,
 * unvalidated answer.
 */
class ClosedLoopFailClosedGuardTest {

  private static final PromptContext CONTEXT =
      new PromptContext("Give me JSON", Map.of("conversationId", "run-1", "userId", "u-1"));

  private final ChatInvocation invocation = mock(ChatInvocation.class);
  private final EnginePipelineBridge pipelineBridge = mock(EnginePipelineBridge.class);
  private final DomainEventPublisher events = mock(DomainEventPublisher.class);
  private final DynamicPipelineExecutor pipeline = mock(DynamicPipelineExecutor.class);

  private static ChatCompletion chunk(String text) {
    return new ChatCompletion(text, null);
  }

  private Flux<InternalChatResponse> streamOf(
      PromptContextInterceptor interceptor, String... chunks) {
    return streamOf(interceptor, CONTEXT, chunks);
  }

  Flux<InternalChatResponse> streamOf(
      PromptContextInterceptor interceptor, PromptContext turn, String... chunks) {
    var request = InternalChatRequest.simple("Give me JSON");
    var prompt = mock(PreparedPrompt.class);
    var context =
        new EnginePipelineContext(
            "ollama", "run-1", "Give me JSON", prompt, List.of(), null, null, turn);
    var interceptors = List.of(interceptor);
    when(pipelineBridge.compileContext(request, pipeline, "ollama", interceptors))
        .thenReturn(context);
    when(invocation.stream(prompt, ChatRoute.platform()))
        .thenReturn(
            Flux.fromArray(
                Arrays.stream(chunks)
                    .map(ClosedLoopFailClosedGuardTest::chunk)
                    .toArray(ChatCompletion[]::new)));
    var bridge =
        new EngineStreamBridge(invocation, pipelineBridge, mock(UserCredentialsProvider.class));
    return bridge.createStream(request, pipeline, "ollama", interceptors, events);
  }

  private static ClosedLoopValidationInterceptor deterministicTiers() {
    var props = new ClosedLoopValidationProperties(true, 0, true, true, false, false);
    return new ClosedLoopValidationInterceptor(props, null, null, null);
  }

  @Test
  @DisplayName("a streamed answer that fails Tier A is stopped before the stream ends")
  void streamedTierAFailureIsStopped() {
    Flux<InternalChatResponse> stream = streamOf(deterministicTiers(), "{inva", "lid");

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class);
    verify(events, never()).publish(any(ChatCompletedEvent.class));
  }

  @Test
  @DisplayName("a streamed answer that fails Tier B is stopped before the stream ends")
  void streamedTierBFailureIsStopped() {
    Flux<InternalChatResponse> stream =
        streamOf(deterministicTiers(), "Here:\n```json\n{oops\n", "```\n", "and more text");

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class);
    verify(events, never()).publish(any(ChatCompletedEvent.class));
  }

  @Test
  @DisplayName("a streamed answer that passes ends with a final event carrying the verdict")
  void streamedAnswerEndsWithItsVerdict() {
    List<InternalChatResponse> out =
        streamOf(deterministicTiers(), "{\"ok\":", "true}").collectList().block();

    assertThat(out).isNotEmpty();
    assertThat(out.getLast().metadata()).containsEntry("control.verdict", "VALIDATED");
    assertThat(out.getFirst().metadata()).containsEntry("control.verdict", "PENDING");
  }

  @Test
  @DisplayName("a Tier C judge outage is never Validated: the turn stops, typed")
  void judgeOutageEscalatesTierC() {
    ProviderChatClient judge = mock(ProviderChatClient.class);
    when(judge.completeWithDefault(any(), any()))
        .thenThrow(new IllegalStateException("judge timeout"));
    var props = new ClosedLoopValidationProperties(true, 0, false, false, true, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, judge, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "an unchecked answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }

  @Test
  @DisplayName("a Tier D outage is never Validated: the turn stops, typed")
  void judgeOutageEscalatesTierD() {
    TestShaperPort shaper = mock(TestShaperPort.class);
    when(shaper.generateAssertions(any(), any())).thenThrow(new IllegalStateException("down"));
    var props = new ClosedLoopValidationProperties(true, 0, false, false, false, true);
    var interceptor = new ClosedLoopValidationInterceptor(props, null, null, shaper);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "an unchecked answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }

  @Test
  @DisplayName("a closed invalid code block stops the stream before its chunk is emitted")
  void closedInvalidBlockStopsTheChunk() {
    List<Object> seen = new java.util.ArrayList<>();
    Flux<InternalChatResponse> stream =
        streamOf(deterministicTiers(), "```json\n{oops\n```\n", "more").doOnNext(seen::add);

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class);
    assertThat(seen).isEmpty();
  }

  @Test
  @DisplayName("a judge that answers no readable score is an outage, never a neutral 5")
  void unreadableScoreIsAnOutage() {
    ProviderChatClient judge = mock(ProviderChatClient.class);
    when(judge.completeWithDefault(any(), any())).thenReturn("looks fine to me");
    var props = new ClosedLoopValidationProperties(true, 0, false, false, true, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, judge, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "an unchecked answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }

  @Test
  @DisplayName("a readable judge verdict still validates, and the metadata says so")
  void readableVerdictValidates() {
    ProviderChatClient judge = mock(ProviderChatClient.class);
    when(judge.completeWithDefault(any(), any())).thenReturn("{\"score\": 2}");
    var props = new ClosedLoopValidationProperties(true, 0, false, false, true, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, judge, null, null);

    var screened = interceptor.screen(CONTEXT, "an answer");

    assertThat(screened.text()).isEqualTo("an answer");
    assertThat(screened.notes()).containsEntry("control.verdict", "VALIDATED");
  }

  @Test
  @DisplayName("admin data DETERMINISTIC_ONLY serves on tiers A+B with a visible badge, audited")
  void deterministicOnlyPolicyServesDegraded() {
    ProviderChatClient judge = mock(ProviderChatClient.class);
    when(judge.completeWithDefault(any(), any())).thenThrow(new IllegalStateException("down"));
    var recorder =
        mock(com.krizaka.sovkora.core.domain.ports.outbound.ControlVerdictRecorder.class);
    var props =
        new ClosedLoopValidationProperties(
            true,
            0,
            true,
            false,
            true,
            false,
            null,
            0,
            null,
            com.krizaka.sovkora.core.domain.model.control.JudgeOutagePolicy.DETERMINISTIC_ONLY);
    var interceptor =
        new ClosedLoopValidationInterceptor(
            props, judge, null, null, recorder, null, java.time.Clock.systemUTC());

    var screened = interceptor.screen(CONTEXT, "{\"ok\":true}");

    assertThat(screened.notes())
        .containsEntry("control.verdict", "DEGRADED")
        .containsEntry("control.badge", "verification_unavailable");
    verify(recorder)
        .record(org.mockito.ArgumentMatchers.argThat(r -> "DEGRADED".equals(r.actionTaken())));
  }

  @Test
  @DisplayName("DETERMINISTIC_ONLY still refuses an answer that fails a deterministic tier")
  void deterministicOnlyStillRefusesBadStructure() {
    ProviderChatClient judge = mock(ProviderChatClient.class);
    when(judge.completeWithDefault(any(), any())).thenThrow(new IllegalStateException("down"));
    var props =
        new ClosedLoopValidationProperties(
            true,
            0,
            true,
            false,
            true,
            false,
            null,
            0,
            null,
            com.krizaka.sovkora.core.domain.model.control.JudgeOutagePolicy.DETERMINISTIC_ONLY);
    var interceptor = new ClosedLoopValidationInterceptor(props, judge, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "{broken"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_escalation");
  }
}
