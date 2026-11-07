package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.AssertionContract;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.sovkora.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClosedLoopValidationInterceptorTest {

  private static final PromptContext CTX =
      new PromptContext("prompt", Map.of("conversationId", "run-1"));

  @Mock private ProviderChatClient chatModel;

  @ParameterizedTest(name = "Tier A - {0}")
  @CsvSource({
    "Valid JSON passes schema validation silently, true, 3, '{\"key\": \"value\"}'",
    "Non-JSON text passes when no JSON block detected, true, 3, 'This is a plain text response without JSON.'"
  })
  void tierA_validationScenarios(
      String description, boolean schemaStrict, int maxRetries, String responseText) {
    var props =
        new ClosedLoopValidationProperties(true, maxRetries, schemaStrict, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThat(advisor.screenResponse(CTX, responseText)).isEqualTo(responseText);
  }

  @Test
  @DisplayName("Tier A — invalid JSON with nothing to adjust it escalates")
  void tierA_invalidJsonEscalates() {
    var props = new ClosedLoopValidationProperties(true, 1, true, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThatThrownBy(() -> advisor.screenResponse(CTX, "{invalid json"))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("Tier C — Advocate wins, no retry triggered")
  void tierC_advocateWinsNoRetry() {
    var props = new ClosedLoopValidationProperties(true, 3, false, false, true, false);

    // Critic scores low (3), Advocate scores high (8) — Advocate wins
    String criticResponse = "{\"score\": 3, \"issues\": []}";
    String advocateResponse = "{\"score\": 8, \"strengths\": [\"solid\"]}";

    when(chatModel.completeWithDefault(any(), any())).thenReturn(criticResponse, advocateResponse);

    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThat(advisor.screenResponse(CTX, "def foo(): return 42"))
        .isEqualTo("def foo(): return 42");
  }

  @Test
  @DisplayName("Tier C — Critic wins every time: the adjustments are spent, then it escalates")
  void tierC_criticWinsRetriesExhausted() {
    var props = new ClosedLoopValidationProperties(true, 1, false, false, true, false);
    var recorder = mock(ControlVerdictRecorder.class);

    when(chatModel.completeWithDefault(any(), any()))
        .thenAnswer(
            call -> {
              List<EngineMessage> messages = call.getArgument(0);
              String system = messages.getFirst().text();
              if (system.contains("ruthless")) {
                return "{\"score\": 9, \"issues\": [\"logical flaw\"]}";
              }
              if (system.contains("advocate")) {
                return "{\"score\": 2, \"strengths\": []}";
              }
              return "def foo(): return still_wrong";
            });

    var advisor =
        new ClosedLoopValidationInterceptor(
            props,
            chatModel,
            null,
            null,
            recorder,
            null,
            Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> advisor.screenResponse(CTX, "def foo(): return None"))
        .isInstanceOf(PipelineShortCircuitException.class);

    var records = ArgumentCaptor.forClass(ControlVerdictRecord.class);
    verify(recorder, times(2)).record(records.capture());
    assertThat(records.getAllValues())
        .extracting(ControlVerdictRecord::actionTaken)
        .containsExactly("ADJUSTED", "ESCALATED");
    assertThat(records.getAllValues().get(1).runId()).isEqualTo("run-1");
    assertThat(records.getAllValues().get(1).timestamp())
        .isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
    assertThat(records.getAllValues().get(1).driftReason()).startsWith("SEMANTIC_C");
  }

  @Test
  @DisplayName("Disabled advisor does nothing")
  void disabledAdvisorNoOp() {
    var props = new ClosedLoopValidationProperties(false, 3, true, true, true, false);
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThat(advisor.screenResponse(CTX, "{\"key\": \"value\"}"))
        .isEqualTo("{\"key\": \"value\"}");
    verifyNoInteractions(chatModel);
  }

  @Test
  @DisplayName("Null response text does nothing")
  void nullResponseNoOp() {
    var props = ClosedLoopValidationProperties.defaults();
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThat(advisor.screenResponse(CTX, null)).isNull();
    verifyNoInteractions(chatModel);
  }

  @Test
  @DisplayName("isAiDependent reflects Tier C and Tier D state")
  void isAiDependentReflectsTierCAndD() {
    // Tier C enabled with chatModel → AI-dependent
    var enabledC = new ClosedLoopValidationProperties(true, 3, true, false, true, false);
    assertThat(new ClosedLoopValidationInterceptor(enabledC, chatModel, null, null).isAiDependent())
        .isTrue();

    // Tier C disabled, Tier D disabled → not AI-dependent
    var disabledAll = new ClosedLoopValidationProperties(true, 3, true, false, false, false);
    assertThat(
            new ClosedLoopValidationInterceptor(disabledAll, chatModel, null, null).isAiDependent())
        .isFalse();

    // Tier C enabled but no chatModel → not AI-dependent (graceful degradation)
    var noModel = new ClosedLoopValidationProperties(true, 3, true, false, true, false);
    assertThat(new ClosedLoopValidationInterceptor(noModel, null, null, null).isAiDependent())
        .isFalse();

    // Tier D enabled with testShaperPort → AI-dependent
    var tdrPort = mock(com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort.class);
    var enabledD = new ClosedLoopValidationProperties(true, 3, true, false, false, true);
    assertThat(new ClosedLoopValidationInterceptor(enabledD, null, null, tdrPort).isAiDependent())
        .isTrue();
  }

  @Test
  @DisplayName("ClosedLoopValidationProperties self-validates bounds")
  void propertiesSelfValidation() {
    var capped = new ClosedLoopValidationProperties(true, 100, true, true, true, false);
    assertThat(capped.maxRetries()).isEqualTo(5);

    var negative = new ClosedLoopValidationProperties(true, -5, true, true, true, false);
    assertThat(negative.maxRetries()).isZero();
  }

  @Test
  @DisplayName("Should resolve active steps from database repository")
  void resolveActiveStepsFromDb() {
    var repo = mock(ValidationPipelineRepository.class);
    var config =
        new ValidationPipelineConfiguration(
            UUID.randomUUID(), ValidationStepType.SEMANTIC_C, true, 1, Map.of());
    when(repo.findAllOrderedByExecution()).thenReturn(List.of(config));

    var props = new ClosedLoopValidationProperties(true, 3, false, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, repo, null);

    advisor.screenResponse(CTX, "response");
    verify(repo, times(1)).findAllOrderedByExecution();
  }

  @Test
  @DisplayName("Should fallback to static config on db repository exception")
  void resolveActiveStepsFallbackOnError() {
    var repo = mock(ValidationPipelineRepository.class);
    when(repo.findAllOrderedByExecution()).thenThrow(new RuntimeException("DB error"));

    var props = new ClosedLoopValidationProperties(true, 3, true, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, repo, null);

    assertThatCode(() -> advisor.screenResponse(CTX, "invalid json")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier D — Evaluation of must contain / must not contain assertions")
  void tierD_assertionEvaluation() {
    var props = new ClosedLoopValidationProperties(true, 1, false, false, false, true);
    var testShaperPort = mock(TestShaperPort.class);

    var contractPass =
        new AssertionContract(
            "schema-1", List.of("must contain value", "must not contain error"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractPass);

    var advisorPass = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatCode(() -> advisorPass.screenResponse(CTX, "This is the value we want."))
        .doesNotThrowAnyException();

    var contractFail1 = new AssertionContract("schema-1", List.of("must contain value"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractFail1);

    var advisorFail1 = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatThrownBy(() -> advisorFail1.screenResponse(CTX, "Other content."))
        .isInstanceOf(PipelineShortCircuitException.class);

    var contractFail2 = new AssertionContract("schema-1", List.of("must not contain error"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractFail2);

    var advisorFail2 = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatThrownBy(() -> advisorFail2.screenResponse(CTX, "This is an error!"))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("Tier D — Delegates to Tier C when D fails but A/B pass, and Advocate wins")
  void tierD_delegatesToC_advocateWins() {
    var props = new ClosedLoopValidationProperties(true, 1, true, false, true, true);
    var testShaperPort = mock(TestShaperPort.class);

    var contract = new AssertionContract("schema-1", List.of("must contain value"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contract);

    String criticResponse = "{\"score\": 2, \"issues\": []}";
    String advocateResponse = "{\"score\": 8, \"strengths\": []}";
    when(chatModel.completeWithDefault(any(), any())).thenReturn(criticResponse, advocateResponse);

    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, testShaperPort);
    assertThatCode(() -> advisor.screenResponse(CTX, "{\"key\": \"other\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier A — JSON extraction from markdown fences")
  void tierA_jsonExtractionMarkdown() {
    var props = new ClosedLoopValidationProperties(true, 1, true, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThatCode(() -> advisor.screenResponse(CTX, "```json\n{\"key\": \"val\"}\n```"))
        .doesNotThrowAnyException();

    assertThatCode(() -> advisor.screenResponse(CTX, "```\n{\"key\": \"val\"}\n```"))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> advisor.screenResponse(CTX, "```json\n{invalid\n```"))
        .isInstanceOf(PipelineShortCircuitException.class);
  }
}
