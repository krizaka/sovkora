package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/**
 * Guard of sovkora-engine#116 (ADR-092): the control loop re-runs the step with the drift injected,
 * never serves an output that did not pass, and really evaluates Tier B.
 */
class ClosedLoopControlLoopGuardTest {

  private static final PromptContext CONTEXT =
      new PromptContext("Give me JSON", Map.of("conversationId", "run-1", "userId", "u-1"));

  private static ClosedLoopValidationProperties tierAOnly(int maxRetries) {
    return new ClosedLoopValidationProperties(true, maxRetries, true, false, false, false);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<EngineMessage>> messagesCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }

  private static String contents(List<EngineMessage> messages) {
    return messages.stream().map(EngineMessage::text).collect(Collectors.joining("\n"));
  }

  @Test
  @DisplayName("a retry re-runs the step with the drift diff injected and serves the adjusted text")
  void retryReRunsTheStepWithTheDriftDiff() {
    ProviderChatClient model = mock(ProviderChatClient.class);
    when(model.completeWithDefault(any(), any())).thenReturn("{\"ok\":true}");
    var interceptor = new ClosedLoopValidationInterceptor(tierAOnly(2), model, null, null);

    String served = interceptor.screenResponse(CONTEXT, "{invalid");

    assertThat(served).isEqualTo("{\"ok\":true}");
    var prompt = messagesCaptor();
    verify(model).completeWithDefault(prompt.capture(), any());
    assertThat(contents(prompt.getValue()))
        .contains("Invalid JSON structure")
        .contains("{invalid")
        .contains("Give me JSON");
  }

  @Test
  @DisplayName("the second attempt's input differs from the first's")
  void secondAttemptInputDiffersFromTheFirst() {
    ProviderChatClient model = mock(ProviderChatClient.class);
    when(model.completeWithDefault(any(), any())).thenReturn("{still bad", "{\"ok\":true}");
    var interceptor = new ClosedLoopValidationInterceptor(tierAOnly(3), model, null, null);

    interceptor.screenResponse(CONTEXT, "{invalid");

    var prompts = messagesCaptor();
    verify(model, org.mockito.Mockito.times(2)).completeWithDefault(prompts.capture(), any());
    assertThat(contents(prompts.getAllValues().get(1)))
        .isNotEqualTo(contents(prompts.getAllValues().getFirst()));
  }

  @Test
  @DisplayName("after the last retry the turn escalates and the unvalidated text is never served")
  void exhaustedRetriesEscalate() {
    ProviderChatClient model = mock(ProviderChatClient.class);
    when(model.completeWithDefault(any(), any())).thenReturn("{bad one", "{bad two", "{bad three");
    var interceptor = new ClosedLoopValidationInterceptor(tierAOnly(2), model, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "{invalid"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_escalation");
  }

  @Test
  @DisplayName("Tier B is evaluated: an unterminated code block fails it")
  void tierBFailsAFailingFixture() {
    var props = new ClosedLoopValidationProperties(true, 0, false, true, false, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "Here:\n```java\nclass A {"))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("Tier B still passes a well-formed answer")
  void tierBPassesAGoodFixture() {
    var props = new ClosedLoopValidationProperties(true, 0, false, true, false, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, null, null, null);
    String good = "Here:\n```java\nclass A {}\n```";

    assertThat(interceptor.screenResponse(CONTEXT, good)).isEqualTo(good);
  }

  static Stream<Object[]> failingOutputs() {
    return Stream.of("{invalid", "```json\n{oops\n```", "[1, 2")
        .flatMap(text -> Stream.of(0, 1, 3).map(retries -> new Object[] {text, retries}));
  }

  @ParameterizedTest(name = "{0} with {1} retries is never served")
  @MethodSource("failingOutputs")
  @DisplayName("no path serves an output that failed validation (no model, any retry budget)")
  void noPathServesAnUnvalidatedOutput(String failing, int retries) {
    var interceptor = new ClosedLoopValidationInterceptor(tierAOnly(retries), null, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, failing))
        .isInstanceOf(PipelineShortCircuitException.class);
  }
}
