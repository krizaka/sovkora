package com.krizaka.sovkora.jobs.api.api.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The message a producer publishes onto {@code orazaka.jobs} to request asynchronous execution.
 *
 * <p>One record, in one Tier-1 module, because it is a <b>wire contract</b> and it had drifted into
 * two: the conversation service's copy grew {@link #withReservation} and {@link #resolvedModel()}
 * for billing while the job service's copy did not, so the executor was deserialising a shape its
 * own class no longer described (production-readiness audit #17, ADR-037). A contract with two
 * definitions has none.
 *
 * <p>The wire payload stays a generic {@code Map<String, Object>} for (de)serialization and
 * persistence, but business code must read it through the typed "smart payload" accessors below
 * rather than casting raw {@code payload().get(...)} values [ERR-127].
 *
 * @param jobId the id every lifecycle event of this job is keyed by
 * @param userId the opaque actor the work is attributed to; {@code null} for unattributed work
 * @param featureKey the capability to execute — resolved to a queue through {@link
 *     com.krizaka.sovkora.jobs.api.domain.port.CapabilityRoutingClient}, never by inspecting this
 *     string
 * @param model the model the producer resolved, or {@code "default"} when it resolved none
 * @param payload the capability's arguments; defensively copied
 * @param dataClass what the work's material is, declared by the producer that knows (ADR-065). A
 *     typed component and not a payload key: payload keys under {@code orazaka.} can be written by
 *     an end user through door 1, and a class is not theirs to choose. {@code null} here means the
 *     producer declared nothing — which the job plane refuses rather than reading as STANDARD
 */
public record JobCommand(
    String jobId,
    String userId,
    String featureKey,
    String model,
    Map<String, Object> payload,
    DataClass dataClass)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Sentinel written by the 4-argument constructor when the caller resolved no model. */
  private static final String UNRESOLVED_MODEL = "default";

  private static final String HOLD_ID_KEY = "holdId";

  /**
   * Payload key by which a producer claims the metering of the inference it is dispatching.
   *
   * <p>Public because it is a <b>wire key</b>, not an implementation detail: three modules in three
   * bounded contexts have to agree on the exact string — the two producers that set it and the
   * interceptor that obeys it — and the only sanctioned way for one context to depend on another's
   * vocabulary is a Tier-1 contract (AGENTS.md §2). It lived as a private copy in each of them
   * until the second producer was found to have no copy at all, and the ledger showed what that
   * cost (ADR-045).
   *
   * <p>Namespaced {@code orazaka.metering.} rather than under any one producer's namespace, so no
   * pack or Studio vocabulary reaches engine code that must read it [PACK-002].
   */
  public static final String DEFERRED_METERING_KEY = "orazaka.metering.deferred";

  /**
   * Payload key by which a producer declares which preference namespace the pipeline should enrich
   * a prompt from.
   *
   * <p>The enrichment interceptor used to name {@code "orazaka.studio.brand."} itself, which meant
   * the engine enriched exactly one namespace and it was the Studio product's. An external pack
   * could not have a context of its own without editing an engine library — the coupling ADR-049 §6
   * found and could not repair without deciding this (ADR-050).
   *
   * <p>Declared, not inferred, for the third time in this codebase: the producer states the
   * namespace it owns, and the engine enriches whatever it is told rather than what it was compiled
   * to know. A blueprint step can set this among its inputs, so a pack brings its own context with
   * no engine change at all.
   *
   * <p>Namespaced {@code orazaka.enrichment.} — the engine's own, since the mechanism is the
   * engine's and only the value belongs to a pack.
   */
  public static final String ENRICHMENT_NAMESPACE_KEY = "orazaka.enrichment.namespace";

  /**
   * Payload keys by which a producer declares the domain this turn must refuse (ADR-051).
   *
   * <p>The fourth application of AGENTS.md §12, and the one where getting it wrong is a compliance
   * failure rather than a billing one. The engine holds the interceptor; the pack holds the
   * subject. A legal-drafting pack refusing legal advice and a medical pack refusing diagnosis are
   * the same code and two manifests — naming either domain here would make the third such pack a
   * deploy, which is what {@code regulatory_class} exists to prevent.
   *
   * <p>{@code …terms} is the comma-separated list whose presence refuses the turn; {@code …refusal}
   * is the exact answer given instead, written by the pack's author and never generated.
   */
  public static final String SCOPE_REFUSED_TERMS_KEY = "orazaka.scope.refused-terms";

  /** The fixed answer a refused turn receives; see {@link #SCOPE_REFUSED_TERMS_KEY}. */
  public static final String SCOPE_REFUSAL_KEY = "orazaka.scope.refusal";

  /**
   * What a {@code REGULATED} pack treats as crisis content, comma-separated, declared by the pack.
   *
   * <p>Never a list the engine holds: a wellbeing pack in French and a bereavement pack in Dutch
   * watch for different words, and an engine-side list would be one of them (AGENTS.md §12).
   */
  public static final String SAFETY_CRISIS_TERMS_KEY = "orazaka.safety.crisis-terms";

  /**
   * The fixed, human-reviewed reply a crisis turn receives, with its region's verified resource
   * already appended by the producer.
   *
   * <p>Carried whole rather than assembled downstream, because the one thing that must never happen
   * is a model composing this text — it will produce a number that looks like a crisis line
   * (ADR-055 §4).
   */
  public static final String SAFETY_RESPONSE_KEY = "orazaka.safety.response";

  /**
   * <b>What the user actually wrote</b>, declared by the producer that knows.
   *
   * <p>A guard that matches the assembled prompt matches the pack's own instructions. A wellbeing
   * blueprint whose template says <i>"tu ne poses aucun diagnostic"</i> contains the exact word its
   * scope guard refuses, so every legitimate entry was refused by the pack's own good intentions
   * (ADR-055 §7). The engine cannot tell the user's words from the template it was pasted into —
   * only the producer that assembled them can, so the producer says so. Absent, a guard falls back
   * to the raw query, which for an ordinary chat turn IS what the user wrote.
   */
  public static final String GUARD_SUBJECT_KEY = "orazaka.guard.subject";

  private static final String CORRELATION_ID_KEY = "correlationId";

  /** Compact canonical constructor enforcing the contract's invariants (ERR-106). */
  public JobCommand {
    Objects.requireNonNull(jobId, "Job ID cannot be null");
    Objects.requireNonNull(featureKey, "Feature key cannot be null");
    Objects.requireNonNull(model, "Model cannot be null");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
  }

  /**
   * Overloaded constructor for callers that resolved no model.
   *
   * @param jobId the job id
   * @param userId the opaque actor
   * @param featureKey the capability to execute
   * @param payload the capability's arguments
   */
  public JobCommand(String jobId, String userId, String featureKey, Map<String, Object> payload) {
    this(jobId, userId, featureKey, UNRESOLVED_MODEL, payload, null);
  }

  /**
   * A command whose producer declares no data class — built by callers that never reach the job
   * plane's intake (executor tests, payload helpers). The job plane refuses one (ADR-065).
   *
   * @param jobId the job id
   * @param userId the opaque actor
   * @param featureKey the capability to execute
   * @param model the model the producer resolved
   * @param payload the capability's arguments
   */
  public JobCommand(
      String jobId, String userId, String featureKey, String model, Map<String, Object> payload) {
    this(jobId, userId, featureKey, model, payload, null);
  }

  // ── Billing (ADR-033 §6.3: the payload carries holdId + correlationId) ────

  /**
   * Returns a copy of this command carrying its credit reservation.
   *
   * <p>The hold travels in the payload rather than in a new record component because the executor
   * and the settlement consumer read the same message: an additive payload key is backward
   * compatible with in-flight messages published before billing existed, where a new required
   * component would not be.
   *
   * @param holdId the reservation taken before this job was published
   * @param correlationId the id every hold of one run shares, for the §6.5 roll-up
   * @return the stamped command
   */
  public JobCommand withReservation(String holdId, String correlationId) {
    Map<String, Object> stamped = new HashMap<>(payload);
    stamped.put(HOLD_ID_KEY, holdId);
    stamped.put(CORRELATION_ID_KEY, correlationId);
    return new JobCommand(jobId, userId, featureKey, model, stamped, dataClass);
  }

  /**
   * Returns a copy of this command declaring that its producer meters the inference.
   *
   * <p>The pipeline's {@code EntitlementInterceptor} takes a per-turn credit hold on the stated
   * assumption that "this turn is synchronous, there is no async job behind it". Both producers of
   * {@code job.*} commands are exactly the case that excludes, and both had already reserved: a
   * Studio run holds once for the whole run, and an async submission holds per job before it is
   * queued. Without this declaration the interceptor held a second time and the work was billed
   * twice — measured, not inferred (ADR-044, ADR-045).
   *
   * <p>Declared by the producer rather than inferred by the interceptor: sniffing for a {@code
   * jobId} or a {@code holdId} would make every future producer that happens to carry one silently
   * unbilled, whereas a marker is a claim its author is accountable for. Build-enforced by {@code
   * MeteringMarkerRules} [BILL-001].
   *
   * @return the stamped command
   */
  public JobCommand withDeferredMetering() {
    Map<String, Object> stamped = new HashMap<>(payload);
    stamped.put(DEFERRED_METERING_KEY, Boolean.TRUE);
    return new JobCommand(jobId, userId, featureKey, model, stamped, dataClass);
  }

  /**
   * @return whether the producer of this command claims to meter the inference itself
   */
  public boolean deferredMetering() {
    return Boolean.TRUE.equals(payload.get(DEFERRED_METERING_KEY))
        || "true".equals(String.valueOf(payload.get(DEFERRED_METERING_KEY)));
  }

  /**
   * @return the reservation to settle or release once this job reaches a terminal state, or {@code
   *     null} when the job was not metered — which is a valid state, not an error
   */
  public String holdId() {
    return payload.get(HOLD_ID_KEY) instanceof String s && !s.isBlank() ? s : null;
  }

  /**
   * @return the id tying every hold of one run together; {@code null} when the job was not metered
   */
  public String correlationId() {
    return payload.get(CORRELATION_ID_KEY) instanceof String s && !s.isBlank() ? s : null;
  }

  /**
   * The model to price this job against: the payload override first, then the resolved model,
   * {@code null} when neither was decided — which asks the pricebook for the capability default.
   *
   * @return the model name, or {@code null} to let the pricebook decide
   */
  public String resolvedModel() {
    String requested = requestedModel();
    if (requested != null && !requested.isBlank()) {
      return requested;
    }
    return UNRESOLVED_MODEL.equals(model) ? null : model;
  }

  // ── Typed payload accessors (smart payload) ──────────────────────────────

  /**
   * @return the prompt text, checking the "prompt" key then "text"; {@code null} if neither is
   *     present
   */
  public String prompt() {
    Object value = payload.get("prompt");
    if (value == null) {
      value = payload.get("text");
    }
    return value instanceof String s ? s : null;
  }

  /**
   * The prompt text, throwing if absent.
   *
   * @return the prompt text
   * @throws IllegalArgumentException if neither "prompt" nor "text" is present.
   */
  public String requirePrompt() {
    String prompt = prompt();
    if (prompt == null) {
      throw new IllegalArgumentException("Payload does not contain prompt or text field");
    }
    return prompt;
  }

  /**
   * @return the explicit model override carried in the payload ("model" key); {@code null} if
   *     absent
   */
  public String requestedModel() {
    return payload.get("model") instanceof String s ? s : null;
  }

  /**
   * @return the voice id for speech synthesis ("voice" key); {@code null} if absent
   */
  public String voice() {
    return payload.get("voice") instanceof String s ? s : null;
  }

  /**
   * @return the requested duration in seconds ("durationSeconds" key); {@code null} if absent
   */
  public Integer durationSeconds() {
    return payload.get("durationSeconds") instanceof Integer i ? i : null;
  }

  /**
   * @return the input image path for image-to-video ("imagePath" key); {@code null} if absent
   */
  public String imagePath() {
    return payload.get("imagePath") instanceof String s ? s : null;
  }

  /**
   * @return the input media file path ("filePath" key); {@code null} if absent
   */
  public String filePath() {
    return payload.get("filePath") instanceof String s ? s : null;
  }
}
