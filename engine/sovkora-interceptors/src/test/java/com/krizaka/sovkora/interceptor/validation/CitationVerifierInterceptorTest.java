package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.pipeline.ResponseScreen;
import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CitationVerifierInterceptorTest {

  private final List<VerifyMode> modes = new ArrayList<>();
  private final List<List<ReadDocument>> reads = new ArrayList<>();
  private final List<GroundedAnswer> seen = new ArrayList<>();

  private CitationVerifierInterceptor interceptor(String mode) {
    CitationVerifier verifier =
        (answer, m, read) -> {
          modes.add(m);
          reads.add(read);
          seen.add(answer);
          return new GroundedAnswer(
              answer.answer(),
              answer.claims().stream()
                  .map(
                      c ->
                          new Claim(
                              c.text(),
                              ClaimStatus.TO_VALIDATE,
                              com.krizaka.sovkora.core.domain.model.research.ClaimReason.NO_SOURCE,
                              c.citations()))
                  .toList(),
              answer.sources());
        };
    return interceptor(verifier, mode, true, true);
  }

  /** The screen with its two switches: the global one and its own pipeline row. */
  private static CitationVerifierInterceptor interceptor(
      CitationVerifier verifier, String mode, boolean researchOn, boolean rowOn) {
    RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
    when(config.getString("research.verify.mode", "MARK")).thenReturn(mode);
    when(config.getBoolean("research.enabled", false)).thenReturn(researchOn);
    PipelineConfigProvider rows = mock(PipelineConfigProvider.class);
    when(rows.findAllOrdered())
        .thenReturn(
            List.of(
                new InterceptorConfig(
                    "CitationVerifierInterceptor", "Citations", 40, rowOn, "verifies citations")));
    return new CitationVerifierInterceptor(verifier, config, rows);
  }

  private static final String CITED =
      """
      {"answer":"x","claims":[{"text":"Trente jours.","status":"VERIFIED","citations":[]}],"sources":[]}
      """;

  @Test
  @DisplayName(
      "a verifier that throws stops the turn: the screen fails closed, through ResponseScreen")
  void aThrowingVerifierStopsTheTurn() {
    CitationVerifierInterceptor stage =
        interceptor(
            (answer, m, read) -> {
              throw new IllegalStateException("lookup down");
            },
            "MARK",
            true,
            true);
    PromptContext context = new PromptContext("q", java.util.Map.of());

    assertThat(stage.failsClosed()).isTrue();
    assertThatThrownBy(() -> ResponseScreen.apply(List.of(stage), context, CITED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("lookup down");
  }

  @Test
  @DisplayName(
      "OFF means no rewrite: its pipeline row off, or research off, leaves the answer untouched")
  void aDisabledScreenRewritesNothing() {
    CitationVerifier verifier =
        (answer, m, read) -> {
          throw new AssertionError("a disabled screen must not verify");
        };
    PromptContext context = new PromptContext("q", java.util.Map.of());

    assertThat(
            ResponseScreen.apply(
                List.of(interceptor(verifier, "STRICT", true, false)), context, CITED))
        .isEqualTo(CITED);
    assertThat(
            ResponseScreen.apply(
                List.of(interceptor(verifier, "STRICT", false, true)), context, CITED))
        .isEqualTo(CITED);
  }

  @Test
  @DisplayName("a screen with no pipeline row at all is off")
  void noRowIsOff() {
    RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
    when(config.getBoolean("research.enabled", false)).thenReturn(true);
    PipelineConfigProvider rows = mock(PipelineConfigProvider.class);
    when(rows.findAllOrdered()).thenReturn(List.of());
    CitationVerifierInterceptor stage =
        new CitationVerifierInterceptor(
            (answer, m, read) -> {
              throw new AssertionError("no row, no screen");
            },
            config,
            rows);

    assertThat(stage.screenResponse(new PromptContext("q", java.util.Map.of()), CITED))
        .isEqualTo(CITED);
  }

  @Test
  @DisplayName("an answer that is not in the citation shape passes through untouched")
  void otherAnswersPassThrough() {
    CitationVerifierInterceptor stage = interceptor("MARK");
    PromptContext context = mock(PromptContext.class);
    assertThat(stage.screenResponse(context, "Bonjour !")).isEqualTo("Bonjour !");
    assertThat(stage.screenResponse(context, "{\"answer\":\"x\"}")).isEqualTo("{\"answer\":\"x\"}");
    assertThat(stage.screenResponse(context, "{not json")).isEqualTo("{not json");
    assertThat(seen).isEmpty();
  }

  @Test
  @DisplayName(
      "a citation-shaped answer is verified against what this turn read; the model's status is not"
          + " passed on; the mode is read")
  void citationShapeIsVerified() {
    String model =
        """
        {"answer":"Trente jours.","claims":[{"text":"Trente jours.","status":"VERIFIED",
         "citations":[{"url":"https://example.org/r","quote":"trente jours","contentHash":"%s","fetchedAt":"t"}]}],
         "sources":[{"url":"https://example.org/r","title":"R","sourceKey":"site"}]}
        """
            .formatted("a".repeat(64));
    ReadDocument read = new ReadDocument("site", "https://example.org/r", "a".repeat(64));
    PromptContext context =
        new PromptContext("q", java.util.Map.of()).withResearchRead(List.of(read));
    String served = interceptor("STRICT").screenResponse(context, model);
    assertThat(modes).containsExactly(VerifyMode.STRICT);
    assertThat(reads).containsExactly(List.of(read));
    assertThat(seen.getFirst().claims().getFirst().status()).isEqualTo(ClaimStatus.TO_VALIDATE);
    assertThat(served)
        .contains(
            "\"status\":\"TO_VALIDATE\"", "\"reason\":\"NO_SOURCE\"", "\"sourceKey\":\"site\"");
  }
}
