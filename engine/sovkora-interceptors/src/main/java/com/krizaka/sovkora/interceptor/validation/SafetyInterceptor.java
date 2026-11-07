package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.pipeline.ScreenedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stops a turn that carries crisis content and answers it with the pack's fixed, reviewed text.
 *
 * <p>Three properties, and each was learned somewhere else in this system before it was needed
 * here.
 *
 * <p><b>First in the chain.</b> Ahead of {@code ScopeGuardInterceptor}, which is itself ahead of
 * everything that spends. Measured with the scope guard (ADR-051 §3): a refusal that runs first
 * costs nothing, because nothing has been enriched, routed, refined or written into a history yet.
 * The order between the two guards is not cosmetic either — a message that says both <i>"je veux
 * mourir"</i> and <i>"quel médicament"</i> must get the crisis response, not the scope refusal.
 * Whichever runs first decides which answer a person in crisis reads.
 *
 * <p><b>{@code isAiDependent() == false}.</b> {@code orazaka.security.disable-ai=true} fails every
 * AI-dependent interceptor. A safety control that failed with them would be absent exactly when the
 * platform is running degraded, which is when it is most likely to be needed. This one reads a
 * declaration and a string; it calls nothing.
 *
 * <p><b>The response is never generated.</b> It arrives whole, written by the pack author and
 * reviewed by a person, with the region's verified crisis line already appended. Asking a model to
 * compose a crisis reply produces something shaped like one — including a phone number it invented,
 * which a person in crisis will dial (ADR-055 §4).
 *
 * <p>Inert for a pack that declares nothing, which is every pack below {@code REGULATED}.
 */
public class SafetyInterceptor implements PromptContextInterceptor {

  private static final Logger log = LoggerFactory.getLogger(SafetyInterceptor.class);

  /** The refusal code the transport maps to a status; the message is the pack's reviewed text. */
  static final String REASON = "crisis_content";

  /**
   * The code for a turn this guard could not read — an image travels with it (ADR-063). The answer
   * is the same reviewed text; the code says it was given because the guard could not look, not
   * because it found crisis content.
   */
  static final String REASON_UNEXAMINED = "subject_not_examinable";

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  @Override
  public PromptContext beforeExecution(PromptContext context) {
    List<String> terms = declaredTerms(context);
    if (terms.isEmpty()) {
      return context;
    }
    String response = String.valueOf(context.userMetadata().get(JobCommand.SAFETY_RESPONSE_KEY));
    String turn = turnOf(context);
    String matched = turn.isBlank() ? null : firstMatch(terms, turn);
    if (matched != null) {
      // Which rule fired, never what it matched. The term used to be logged "because it is the
      // pack's word, not the user's" — but on a crisis guard the match IS the disclosure: a line
      // naming the term says what this person wrote, in the least protected place it could go
      // (ADR-065). The rule and its reason code are enough to find the refusal; the words are not
      // needed to operate it.
      log.info("Crisis guard fired ({}); answering the reviewed text", REASON);
      throw new PipelineShortCircuitException(getId(), REASON, response, null);
    }
    if (context.carriesMedia()) {
      // An image travels with this turn and this guard reads words: a photographed note sent with
      // "look at this" matches no term and says everything. Passing it would be the false negative
      // this class exists to prevent — someone in crisis sent to a language model — and answering
      // the reviewed text is the false positive it accepts by design (see firstMatch). A pack that
      // wants images read before this answer needs a guard that can read them (ADR-063).
      log.info(
          "A turn carrying media reached a declared crisis guard; answering the reviewed text");
      throw new PipelineShortCircuitException(getId(), REASON_UNEXAMINED, response, null);
    }
    return context;
  }

  /**
   * The same guard, read on the way out: an answer that carries a declared crisis term — a fresh
   * model answer or a cached one, both take this path — is replaced by the reviewed text, never
   * served. A cache must not be a way around this control.
   */
  @Override
  public String screenResponse(PromptContext context, String responseText) {
    List<String> terms = declaredTerms(context);
    if (terms.isEmpty() || responseText == null || responseText.isBlank()) {
      return responseText;
    }
    if (firstMatch(terms, responseText) != null) {
      log.info("Crisis guard fired on an answer ({}); serving the reviewed text", REASON);
      throw new PipelineShortCircuitException(
          getId(),
          REASON,
          String.valueOf(context.userMetadata().get(JobCommand.SAFETY_RESPONSE_KEY)),
          null);
    }
    return responseText;
  }

  /**
   * The same guard on a streamed answer, deterministic and cheap: read on everything streamed so
   * far, before the chunk leaves, so a crisis term split across two chunks is still caught and the
   * stream stops (sovkora-engine#127).
   */
  @Override
  public void screenChunk(PromptContext context, String textSoFar) {
    screenResponse(context, textSoFar);
  }

  /** The assembled streamed answer, once more before the final event. */
  @Override
  public ScreenedResponse screenStreamed(PromptContext context, String assembled) {
    return ScreenedResponse.plain(screenResponse(context, assembled));
  }

  /**
   * Runs whatever the kill-switch is doing.
   *
   * @return always {@code false} — a safety control that switches off with the models is a safety
   *     control that is missing when the platform is degraded
   */
  @Override
  public boolean isAiDependent() {
    return false;
  }

  /**
   * A control: a failure it did not anticipate stops the turn instead of passing it unexamined
   * (ADR-064). Before the posture was declared, an exception here was swallowed by the pipeline and
   * the turn went on as if the guard had found nothing.
   */
  @Override
  public boolean failsClosed() {
    return true;
  }

  private static List<String> declaredTerms(PromptContext context) {
    Object declared = context.userMetadata().get(JobCommand.SAFETY_CRISIS_TERMS_KEY);
    Object response = context.userMetadata().get(JobCommand.SAFETY_RESPONSE_KEY);
    if (!(declared instanceof String terms)
        || terms.isBlank()
        || !(response instanceof String text)
        || text.isBlank()) {
      // Half a declaration guards nothing: terms with no response would short-circuit into silence,
      // which for this interceptor means a person in crisis reading an empty reply.
      return List.of();
    }
    return Arrays.stream(terms.split(","))
        .map(String::trim)
        .filter(term -> !term.isEmpty())
        .toList();
  }

  /**
   * What the user actually wrote.
   *
   * <p>The raw query, not the refined prompt: refinement runs later, and a guard reading its output
   * would be judging the platform's paraphrase of what someone said rather than what they said.
   */
  private static String turnOf(PromptContext context) {
    // The DECLARED subject wins when the producer supplied one (ADR-055 §7). A Studio step's
    // payload prompt is the blueprint's template with the user's words pasted inside it, and
    // matching that matches the pack's own instructions: a template saying "tu ne poses aucun
    // diagnostic" contains the very word this guard refuses. Only the producer that assembled the
    // two can tell them apart, so it says which is which.
    Object declaredSubject = context.userMetadata().get(JobCommand.GUARD_SUBJECT_KEY);
    if (declaredSubject instanceof String subject && !subject.isBlank()) {
      return subject;
    }
    // Otherwise the turn itself. A bare "prompt" key used to be read here as a Studio step's
    // payload prompt — but the job plane has carried only orazaka.* payload keys into a context
    // since before this guard existed, so the only party that could ever supply it was the user,
    // through a stored preference. A guard's subject is not the user's to write (ADR-064).
    return context.rawUserQuery() == null ? "" : context.rawUserQuery();
  }

  /**
   * The first declared term the turn contains.
   *
   * <p><b>Substring, not word boundary</b> — and this is the one place that choice is deliberate.
   * The scope guard matches on boundaries so a pack refusing "avocat" does not refuse "avocatier":
   * there, a false positive is a working product refusing an innocent question. Here the two errors
   * are not symmetric. A false positive shows someone a crisis line they did not need; a false
   * negative sends someone in crisis to a language model. Case and accents are folded for the same
   * reason.
   */
  private static String firstMatch(List<String> terms, String turn) {
    String haystack = fold(turn);
    for (String term : terms) {
      if (haystack.contains(fold(term))) {
        return term;
      }
    }
    return null;
  }

  /** Lower-cased and stripped of combining marks, so an accent is not a way through. */
  private static String fold(String value) {
    return COMBINING_MARKS
        .matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT);
  }
}
