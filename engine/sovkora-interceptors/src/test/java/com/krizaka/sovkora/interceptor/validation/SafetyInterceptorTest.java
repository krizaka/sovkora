package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The crisis guard: fixed text, no model, and no way to switch it off (ADR-055 §4). */
class SafetyInterceptorTest {

  private static final String RESPONSE =
      """
      Je ne suis pas en mesure de vous accompagner sur ce que vous traversez, et je ne veux pas \
      vous laisser seul·e avec ça. Des personnes formées répondent, tout de suite :

      3114 — 3114 (24h/24 et 7j/7, gratuit)""";

  private final SafetyInterceptor interceptor = new SafetyInterceptor();

  private static Map<String, Object> declaring(String terms) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(JobCommand.SAFETY_CRISIS_TERMS_KEY, terms);
    metadata.put(JobCommand.SAFETY_RESPONSE_KEY, RESPONSE);
    return metadata;
  }

  private static PromptContext turn(String text, Map<String, Object> metadata) {
    return new PromptContext(text, metadata);
  }

  @Test
  @DisplayName("a crisis turn is answered with the reviewed text, verbatim")
  void answersTheReviewedText() {
    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(
                    turn("je ne veux plus vivre", declaring("je ne veux plus vivre,en finir"))))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(RESPONSE);
  }

  @Test
  @DisplayName(
      "[ADR-063] an image under a declared crisis guard is answered with the reviewed text")
  void answersTheReviewedTextForMediaItCannotRead() {
    // A photographed note sent with "regarde" matches no term and may say everything. Passing it is
    // the false negative this guard exists to prevent; the reviewed text is the false positive it
    // accepts by design.
    PromptContext photographedNote =
        new PromptContext("regarde", declaring("en finir"), Map.of(), "regarde", null, null, 1);

    assertThatThrownBy(() -> interceptor.beforeExecution(photographedNote))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(RESPONSE)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo(SafetyInterceptor.REASON_UNEXAMINED);
  }

  @Test
  @DisplayName("[ADR-063] no declaration, no guard — an image below REGULATED passes")
  void mediaWithoutADeclarationPassesThrough() {
    PromptContext context =
        new PromptContext("regarde", new HashMap<>(), Map.of(), "regarde", null, null, 1);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("it survives the disable-ai kill-switch: it calls nothing")
  void isNotAiDependent() {
    // orazaka.security.disable-ai fails every AI-dependent interceptor. A safety control that went
    // with them would be absent exactly when the platform is degraded.
    assertThat(interceptor.isAiDependent()).isFalse();
  }

  @Test
  @DisplayName("a pack that declares nothing is untouched — every pack below REGULATED")
  void isInertWithoutADeclaration() {
    PromptContext context = turn("je ne veux plus vivre", new HashMap<>());
    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("half a declaration guards nothing rather than short-circuiting into silence")
  void refusesHalfADeclaration() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(JobCommand.SAFETY_CRISIS_TERMS_KEY, "en finir");
    PromptContext context = turn("je veux en finir", metadata);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("accents and case are not a way through")
  void foldsAccentsAndCase() {
    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(
                    turn("Je Veux EN FINIR ce soir", declaring("en finir"))))
        .isInstanceOf(PipelineShortCircuitException.class);
    assertThatThrownBy(
            () -> interceptor.beforeExecution(turn("je veux en finir", declaring("en finír"))))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("it matches inside a word, unlike the scope guard — the errors are not symmetric")
  void matchesAsSubstring() {
    // The scope guard uses word boundaries so a pack refusing "avocat" does not refuse
    // "avocatier": there a false positive is a working product refusing an innocent question.
    // Here a false positive shows someone a crisis line they did not need, and a false negative
    // sends someone in crisis to a language model.
    assertThatThrownBy(
            () ->
                interceptor.beforeExecution(turn("jenevveuxplusvivre", declaring("veuxplusvivre"))))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("[ADR-064] a bare prompt key is not a subject: only the producer's declaration is")
  void aBarePromptKeyIsNotReadAsTheSubject() {
    // Asserted the opposite on hand-built metadata that no production path ever delivered.
    Map<String, Object> metadata = declaring("en finir");
    metadata.put("prompt", "Aide-moi à écrire : je veux en finir.");
    PromptContext context = turn("bonne semaine", metadata);

    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
  }

  @Test
  @DisplayName("the refusal is a crisis code, so a transport can tell it from a scope refusal")
  void carriesItsOwnReason() {
    assertThatThrownBy(() -> interceptor.beforeExecution(turn("en finir", declaring("en finir"))))
        .asInstanceOf(
            org.assertj.core.api.InstanceOfAssertFactories.type(
                PipelineShortCircuitException.class))
        .extracting(PipelineShortCircuitException::reason)
        .isEqualTo("crisis_content");
  }

  @Test
  @DisplayName("it judges what the USER wrote, not the template around it")
  void matchesTheDeclaredSubject() {
    Map<String, Object> metadata = declaring("en finir");
    metadata.put(JobCommand.GUARD_SUBJECT_KEY, "bonne semaine.");

    PromptContext context =
        turn(
            "Si la personne parle d'en finir, arrête-toi. Elle a écrit : bonne semaine.", metadata);
    assertThat(interceptor.beforeExecution(context)).isSameAs(context);
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
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SafetyInterceptor.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  interceptor.beforeExecution(
                      turn("je ne veux plus vivre", declaring("plus vivre"))))
          .isInstanceOf(PipelineShortCircuitException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).isNotEmpty();
    for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
      // On a crisis guard the presence of the match is the disclosure; on a scope guard the term
      // is the user's question in the pack's words. Neither belongs in a log.
      assertThat(event.getFormattedMessage())
          .contains("crisis_content")
          .doesNotContain("plus vivre")
          .doesNotContain("je ne veux plus vivre");
    }
  }

  @Test
  @DisplayName("an answer that carries a declared crisis term is replaced by the reviewed text")
  void screensAnAnswerThatEchoesACrisisTerm() {
    PromptContext ctx = turn("bonjour", declaring("en finir"));

    assertThatThrownBy(() -> interceptor.screenResponse(ctx, "Pour en finir avec cela, voici..."))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(RESPONSE);
  }

  @Test
  @DisplayName("a clean answer passes the output screen unchanged")
  void passesACleanAnswer() {
    PromptContext ctx = turn("bonjour", declaring("en finir"));

    assertThat(interceptor.screenResponse(ctx, "Voici votre facture."))
        .isEqualTo("Voici votre facture.");
  }

  @Test
  @DisplayName("a pack that declares nothing screens nothing")
  void aPackThatDeclaresNothingScreensNothing() {
    assertThat(interceptor.screenResponse(turn("bonjour", Map.of()), "en finir"))
        .isEqualTo("en finir");
  }
}
