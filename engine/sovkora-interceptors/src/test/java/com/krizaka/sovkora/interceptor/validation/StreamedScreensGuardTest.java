package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.pipeline.ResponseScreen;
import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimReason;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guard of sovkora-engine#127: the safety and citation screens apply to a streamed answer, and the
 * per-chunk check reads the same tier configuration as the non-streamed path.
 */
class StreamedScreensGuardTest {

  private static final String REVIEWED = "Reviewed crisis text";
  private static final String UNVERIFIED =
      "{\"answer\":\"x\",\"claims\":[{\"text\":\"c\",\"status\":\"VERIFIED\",\"citations\":[]}],"
          + "\"sources\":[]}";

  private final ClosedLoopFailClosedGuardTest streams = new ClosedLoopFailClosedGuardTest();

  private static PromptContext crisisGuarded() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("conversationId", "run-1");
    metadata.put("userId", "u-1");
    metadata.put(JobCommand.SAFETY_CRISIS_TERMS_KEY, "en finir");
    metadata.put(JobCommand.SAFETY_RESPONSE_KEY, REVIEWED);
    return new PromptContext("Bonjour", metadata);
  }

  private static CitationVerifierInterceptor citations(boolean verifies) {
    CitationVerifier verifier =
        (answer, mode, read) ->
            new GroundedAnswer(
                answer.answer(),
                answer.claims().stream()
                    .map(
                        c ->
                            new Claim(
                                c.text(),
                                verifies ? ClaimStatus.VERIFIED : ClaimStatus.TO_VALIDATE,
                                verifies ? null : ClaimReason.NO_SOURCE,
                                c.citations()))
                    .toList(),
                answer.sources());
    RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
    when(config.getString("research.verify.mode", "MARK")).thenReturn("MARK");
    when(config.getBoolean("research.enabled", false)).thenReturn(true);
    PipelineConfigProvider rows = mock(PipelineConfigProvider.class);
    when(rows.findAllOrdered())
        .thenReturn(
            List.of(
                new InterceptorConfig(
                    "CitationVerifierInterceptor", "Citations", 40, true, "verifies citations")));
    return new CitationVerifierInterceptor(verifier, config, rows);
  }

  @Test
  @DisplayName("a streamed answer carrying a crisis term is stopped, even split across chunks")
  void streamedSafetyRefusal() {
    var stream = streams.streamOf(new SafetyInterceptor(), crisisGuarded(), "Il faut en", " finir");

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(REVIEWED);
  }

  @Test
  @DisplayName("a streamed answer that is clean passes the safety screen")
  void streamedSafetyPasses() {
    var stream = streams.streamOf(new SafetyInterceptor(), crisisGuarded(), "Bonjour", " !");

    assertThatCode(() -> stream.collectList().block()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a streamed citation answer with an unverified claim is refused, not served")
  void streamedCitationRefusal() {
    var stream =
        streams.streamOf(
            citations(false),
            crisisGuarded(),
            UNVERIFIED.substring(0, 20),
            UNVERIFIED.substring(20));

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo(CitationVerifierInterceptor.REASON_UNVERIFIED);
  }

  @Test
  @DisplayName("a streamed citation answer whose claims all verify is served")
  void streamedCitationPasses() {
    assertThatCode(
            () ->
                ResponseScreen.screenStreamed(
                    List.of(citations(true)), crisisGuarded(), UNVERIFIED))
        .doesNotThrowAnyException();
  }

  private static ClosedLoopValidationInterceptor withDbRows(boolean sandboxEnabledInDb) {
    ValidationPipelineRepository repository = mock(ValidationPipelineRepository.class);
    when(repository.findAllOrderedByExecution())
        .thenReturn(
            List.of(
                new ValidationPipelineConfiguration(
                    UUID.randomUUID(),
                    ValidationStepType.SANDBOX_B,
                    sandboxEnabledInDb,
                    1,
                    Map.of())));
    // The static properties say every deterministic tier is on: the DB rows win.
    var props = new ClosedLoopValidationProperties(true, 0, true, true, false, false);
    return new ClosedLoopValidationInterceptor(props, null, repository, null);
  }

  private static final String BAD_JSON_BLOCK = "Here:\n```json\n{oops\n```\n";

  @Test
  @DisplayName("a tier disabled in the DB is skipped per chunk too")
  void disabledTierSkippedPerChunk() {
    assertThatCode(
            () ->
                ResponseScreen.screenChunk(
                    List.of(withDbRows(false)), crisisGuarded(), BAD_JSON_BLOCK))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a tier enabled in the DB is applied per chunk")
  void enabledTierAppliedPerChunk() {
    assertThatThrownBy(
            () ->
                ResponseScreen.screenChunk(
                    List.of(withDbRows(true)), crisisGuarded(), BAD_JSON_BLOCK))
        .isInstanceOf(PipelineShortCircuitException.class);
  }
}
