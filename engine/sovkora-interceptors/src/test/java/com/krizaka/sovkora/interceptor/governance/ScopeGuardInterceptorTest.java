package com.krizaka.sovkora.interceptor.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.RoutingMode;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The domain a pack declared out of bounds, refused with the pack's own words (ADR-051). */
class ScopeGuardInterceptorTest {

  private static final String REFUSAL =
      "Je rédige des documents. Pour un conseil juridique, consultez un professionnel.";

  private final ScopeGuardInterceptor interceptor = new ScopeGuardInterceptor();

  private static PromptContext turn(String query, Map<String, Object> metadata) {
    return new PromptContext(query, metadata, Map.of(), null, null, RoutingMode.DETERMINISTIC);
  }

  private static Map<String, Object> declaring(String terms) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(JobCommand.SCOPE_REFUSED_TERMS_KEY, terms);
    metadata.put(JobCommand.SCOPE_REFUSAL_KEY, REFUSAL);
    return metadata;
  }

  @Test
  @DisplayName("a turn inside the declared refused domain is short-circuited with the pack's text")
  void refusesTheDeclaredDomain() {
    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(
                    turn(
                        "Peux-tu me donner un conseil juridique sur ce bail ?",
                        declaring("conseil juridique, avocat"))))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(REFUSAL);
  }

  @Test
  @DisplayName(
      "the engine names no domain: a pack refusing something else is served by the same code")
  void refusesWhateverThePackDeclared() {
    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(
                    turn("Quel est mon diagnostic ?", declaring("diagnostic, posologie"))))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("a turn outside the declared domain passes through untouched")
  void allowsEverythingElse() {
    PromptContext context =
        turn("Rédige une mise en demeure de trois lignes.", declaring("conseil juridique"));

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("terms match on word boundaries — 'avocat' must not refuse an 'avocatier'")
  void matchesOnWordBoundariesNotSubstrings() {
    PromptContext context = turn("Décris un avocatier en fleur.", declaring("avocat"));

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("[ADR-063] an image under a declared boundary is refused: the guard cannot read it")
  void refusesMediaItCannotExamine() {
    // A lease photographed and sent with "t'en penses quoi ?" matches no term. Passing it would be
    // the guard running, reporting clean, and examining nothing the pack drew its line around.
    PromptContext photographedLease =
        new PromptContext(
            "t'en penses quoi ?",
            declaring("conseil juridique, bail"),
            Map.of(),
            null,
            null,
            RoutingMode.DETERMINISTIC,
            1);

    assertThatThrownBy(() -> interceptor.beforeExecution(photographedLease))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(REFUSAL)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo(ScopeGuardInterceptor.REASON_UNEXAMINED);
  }

  @Test
  @DisplayName("[ADR-063] an image with no words at all is still a subject the guard cannot read")
  void refusesMediaWithABlankTurn() {
    PromptContext imageOnly =
        new PromptContext(
            "", declaring("bail"), Map.of(), null, null, RoutingMode.DETERMINISTIC, 1);

    assertThatThrownBy(() -> interceptor.beforeExecution(imageOnly))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("[ADR-063] a term the text does match keeps its own reason, image or not")
  void aMatchedTermWinsOverTheMediaReason() {
    PromptContext context =
        new PromptContext(
            "un conseil juridique sur ce bail ?",
            declaring("conseil juridique"),
            Map.of(),
            null,
            null,
            RoutingMode.DETERMINISTIC,
            1);

    assertThatThrownBy(() -> interceptor.beforeExecution(context))
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo(ScopeGuardInterceptor.REASON);
  }

  @Test
  @DisplayName("[ADR-063] no declaration, no guard — an image on a STANDARD turn passes")
  void mediaWithoutADeclarationPassesThrough() {
    PromptContext context =
        new PromptContext(
            "t'en penses quoi ?", Map.of(), Map.of(), null, null, RoutingMode.DETERMINISTIC, 1);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("no declaration, no guard — which is every STANDARD pack")
  void noDeclarationPassesThrough() {
    PromptContext context = turn("Donne-moi un conseil juridique.", Map.of());

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("half a declaration is not a declaration: terms with no refusal guard nothing")
  void refusesToShortCircuitIntoSilence() {
    Map<String, Object> half = new LinkedHashMap<>();
    half.put(JobCommand.SCOPE_REFUSED_TERMS_KEY, "conseil juridique");
    PromptContext context = turn("Donne-moi un conseil juridique.", half);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("[ADR-064] a bare prompt key is not a subject: only the producer's declaration is")
  void aBarePromptKeyIsNotReadAsTheSubject() {
    // This test used to assert the opposite on hand-built metadata. No production path ever
    // delivered a bare "prompt" key to the pipeline — the job plane merges only orazaka.* payload
    // keys — so the only author it ever had was a user's stored preference.
    Map<String, Object> metadata = declaring("conseil juridique");
    metadata.put("prompt", "Rédige un conseil juridique pour ce bail.");
    PromptContext context = turn("Rédige une mise en demeure.", metadata);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName(
      "it survives the disable-ai kill-switch: a boundary must not fail open when degraded")
  void isNotAiDependent() {
    assertThat(interceptor.isAiDependent()).isFalse();
  }

  @Test
  @DisplayName("an accent dropped by the user does not open the boundary the pack closed")
  void foldsAccents() {
    // A guard that misses "conseil juridique" written without its accents fails OPEN — it answers
    // the very question the pack said it would refuse. Fail closed against the absent party.
    Map<String, Object> metadata = declaring("résiliation judiciaire");

    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(
                    turn("Peux-tu faire une resiliation judiciaire ?", metadata)))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("folding does not turn a term into a substring: 'avocatier' is still innocent")
  void foldingKeepsWordBoundaries() {
    Map<String, Object> metadata = declaring("avocat");
    PromptContext context = turn("Comment tailler un avocatier ?", metadata);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("it judges what the USER wrote, not the template their words were pasted into")
  void matchesTheDeclaredSubjectAndNotTheTemplate() {
    // The defect this pins, found by running the phase-J adversarial suite: a Studio step's
    // payload prompt is the blueprint's template around the user's words, and a well-written
    // wellbeing template says "tu ne poses aucun diagnostic" — which contains the exact term the
    // pack refuses. Every legitimate entry was refused by the pack's own good intentions.
    Map<String, Object> metadata = declaring("diagnostic");
    metadata.put(
        com.krizaka.sovkora.jobs.domain.model.JobCommand.GUARD_SUBJECT_KEY,
        "j'ai eu du mal à me lever cette semaine.");

    PromptContext context =
        turn(
            """
            Tu ne poses aucun diagnostic et tu ne nommes aucun trouble.

            Ce qu'elle a écrit : j'ai eu du mal à me lever cette semaine.""",
            metadata);
    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("and still refuses when the refused term is in what the user wrote")
  void theDeclaredSubjectIsStillJudged() {
    Map<String, Object> metadata = declaring("diagnostic");
    metadata.put(
        com.krizaka.sovkora.jobs.domain.model.JobCommand.GUARD_SUBJECT_KEY,
        "peux-tu me donner un diagnostic ?");

    assertThatThrownBy(
            () -> interceptor.beforeExecution(turn("Tu ne poses aucun diagnostic.", metadata)))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("[ADR-064] a control: a failure it did not anticipate stops the turn")
  void declaresItselfAControlThatFailsClosed() {
    assertThat(interceptor.failsClosed()).isTrue();
  }

  @Test
  @DisplayName("[ADR-065] the log says which rule fired — never the term, never the turn")
  void logsTheRuleNeverTheMatch() {
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ScopeGuardInterceptor.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  interceptor.beforeExecution(
                      turn("un conseil juridique sur ce bail ?", declaring("conseil juridique"))))
          .isInstanceOf(PipelineShortCircuitException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).isNotEmpty();
    for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
      // On a crisis guard the presence of the match is the disclosure; on a scope guard the term
      // is the user's question in the pack's words. Neither belongs in a log.
      assertThat(event.getFormattedMessage())
          .contains("out_of_declared_scope")
          .doesNotContain("conseil juridique")
          .doesNotContain("un conseil juridique sur ce bail ?");
    }
  }
}
