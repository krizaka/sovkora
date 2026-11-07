package com.krizaka.sovkora.interceptor.governance;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refuses a turn that strays into the domain its pack declared out of bounds (ADR-051).
 *
 * <p>One of the four controls {@code regulatory_class: SENSITIVE} switches on. The other three are
 * properties of stored data; this one is the only one that has to act while the user is waiting,
 * which is why it is an interceptor and not a scheduled job.
 *
 * <p><b>The pack declares the domain; this class never names one.</b> A legal-drafting pack refuses
 * legal advice, a medical-summary pack refuses diagnosis, and both are this code plus a manifest.
 * Naming either here would put a pack's subject matter in an engine library — AGENTS.md §12, and
 * the fourth time that rule has decided a design.
 *
 * <p><b>What this is, honestly.</b> Word-boundary term matching. It catches the question asked
 * plainly and misses the one asked obliquely, and no amount of term-list grooming changes that. It
 * is worth having because of what it replaces: a sentence in a prompt, which is not a control at
 * all (PACK_CATALOGUE §6.2) — a model can be talked out of an instruction and cannot be talked out
 * of a short-circuit. A pack whose boundary needs a classifier should not be SENSITIVE; it should
 * wait for the REGULATED work where a reviewed model has a budget.
 *
 * <p><b>Not AI-dependent</b>, deliberately: the guard must survive the {@code disable-ai}
 * kill-switch. A boundary that stops being enforced exactly when the platform is degraded is a
 * boundary that fails open at the worst moment.
 */
public class ScopeGuardInterceptor implements PromptContextInterceptor {

  private static final Logger log = LoggerFactory.getLogger(ScopeGuardInterceptor.class);

  /** The refusal code the transport maps to a status; the message is the pack's own text. */
  static final String REASON = "out_of_declared_scope";

  /**
   * The refusal code for a turn whose subject this guard could not read — an image travels with it
   * (ADR-063). Distinct from {@link #REASON} because it is not the same finding: nothing was found
   * out of scope; the guard could not look. The trail and the operator should be able to tell.
   */
  static final String REASON_UNEXAMINED = "subject_not_examinable";

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  @Override
  public PromptContext beforeExecution(PromptContext context) {
    List<String> terms = declaredTerms(context);
    if (terms.isEmpty()) {
      // No declaration, no guard. A pack that declares nothing is STANDARD by construction — the
      // installer refuses a SENSITIVE pack with no scope guard, so this branch is the ordinary
      // case and not a hole (ADR-051).
      return context;
    }
    // The refusal is the pack author's own sentence, returned verbatim. A generated apology would
    // be a model speaking about the very domain the pack just refused to speak about.
    String refusal = String.valueOf(context.userMetadata().get(JobCommand.SCOPE_REFUSAL_KEY));
    String turn = turnOf(context);
    String matched = turn.isBlank() ? null : firstMatch(terms, turn);
    if (matched != null) {
      // Which rule fired, never the term: the term is the user's question in the pack's words
      // (ADR-065).
      log.info("Scope guard fired ({}); answering the pack's refusal", REASON);
      throw new PipelineShortCircuitException(getId(), REASON, refusal, null);
    }
    if (context.carriesMedia()) {
      // The subject of this turn is partly an image, and this guard reads words. A lease
      // photographed and sent with "what do you think?" passes a text match on nothing — the guard
      // would run, report clean, and have examined none of what the pack drew its boundary around.
      // So a declared boundary refuses what it cannot see: the failure mode that costs a working
      // turn is recoverable, the one that answers inside the refused domain is not (AGENTS.md §12,
      // the party not in the room). A pack that needs images examined needs a guard that can read
      // them, which is the media pack's decision and not this class's (ADR-063).
      log.info("Refused a turn carrying media the declared scope guard cannot examine");
      throw new PipelineShortCircuitException(getId(), REASON_UNEXAMINED, refusal, null);
    }
    return context;
  }

  /** Policy read from a declaration and a string: no model call, so no kill-switch exposure. */
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
    Object declared = context.userMetadata().get(JobCommand.SCOPE_REFUSED_TERMS_KEY);
    Object refusal = context.userMetadata().get(JobCommand.SCOPE_REFUSAL_KEY);
    if (!(declared instanceof String terms)
        || terms.isBlank()
        || !(refusal instanceof String text)
        || text.isBlank()) {
      // Half a declaration is not a declaration: terms with no refusal would short-circuit into
      // silence, which reads as a broken product rather than as a boundary drawn on purpose.
      return List.of();
    }
    return Arrays.stream(terms.split(","))
        .map(String::trim)
        .filter(term -> !term.isEmpty())
        .toList();
  }

  /**
   * What the user actually asked.
   *
   * <p>The raw query, not the refined prompt: refinement runs earlier in the pipeline and a guard
   * reading its output would be judging the platform's paraphrase instead of the question. The
   * payload's own {@code prompt} is checked too, because a Studio step carries the turn there
   * rather than as a chat message.
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
   * The first declared term the turn actually contains, on word boundaries.
   *
   * <p>Boundaries rather than {@code contains}: a pack refusing "avocat" must not refuse a question
   * about an "avocatier", and a guard that fires on innocent turns is one an operator switches off.
   *
   * <p>Case and accents are folded on both sides. A guard that misses "resiliation judiciaire"
   * because the user dropped an accent fails <em>open</em> — it answers the very question the pack
   * declared it would refuse. Folding cannot widen a term past its word boundaries, so it costs the
   * innocent turn nothing.
   */
  private static String firstMatch(List<String> terms, String turn) {
    String haystack = fold(turn);
    for (String term : terms) {
      Pattern pattern =
          Pattern.compile(
              "\\b" + Pattern.quote(fold(term)) + "\\b", Pattern.UNICODE_CHARACTER_CLASS);
      Matcher matcher = pattern.matcher(haystack);
      if (matcher.find()) {
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
