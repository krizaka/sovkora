package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.pipeline.ScreenedResponse;
import com.krizaka.sovkora.core.domain.model.AssertionContract;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.control.ControlBadge;
import com.krizaka.sovkora.core.domain.model.control.ControlMetadata;
import com.krizaka.sovkora.core.domain.model.control.ControlState;
import com.krizaka.sovkora.core.domain.model.control.ControlVerdict;
import com.krizaka.sovkora.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.sovkora.core.domain.model.control.Degraded;
import com.krizaka.sovkora.core.domain.model.control.DriftAdjustment;
import com.krizaka.sovkora.core.domain.model.control.HumanEscalation;
import com.krizaka.sovkora.core.domain.model.control.JudgeOutagePolicy;
import com.krizaka.sovkora.core.domain.model.control.JudgeUnavailableException;
import com.krizaka.sovkora.core.domain.model.control.Validated;
import com.krizaka.sovkora.core.domain.ports.outbound.CodeSandboxPort;
import com.krizaka.sovkora.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Autonomous multi-tier closed-loop self-correction advisor for the Satsui Engine.
 *
 * <p>Implements a configurable 4-tier validation chain applied <strong>post-inference</strong>:
 *
 * <ul>
 *   <li><strong>Tier A — Deterministic JSON Schema (0-token cost)</strong>: Validates the LLM
 *       response against Jackson schema rules. Forces an instant structured retry if the response
 *       fails parsing. Pure structural check — zero inference tokens consumed.
 *   <li><strong>Tier B — MCP Sandbox Crash-Test</strong>: Extracts code blocks from the response
 *       and delegates compilation/linting to an isolated environment via the MCP client.
 *       Synthesizes feedback from sandbox errors and retries with corrective context.
 *   <li><strong>Tier C — Semantic Consensus Debate</strong>: Spawns temperature-0.0 Critic vs
 *       Advocate personas to judge semantic alignment against user intent. If the Critic score
 *       outweighs the Advocate, triggers a retry with the synthesized critique.
 *   <li><strong>Tier D — Test-Driven Response (TDR)</strong>: Pre-generates test assertion schemas
 *       via a fast reasoning model and validates the LLM response against them. If Tier D fails but
 *       Tiers A and B pass, gracefully delegates to Tier C for final arbitration.
 * </ul>
 *
 * <p><strong>Control loop (ADR-092, sovkora-engine#116)</strong>: the interceptor decides in {@link
 * #screenResponse}, before an answer is served. Each pass ends in a typed {@link ControlVerdict}:
 * {@link Validated} serves the text; {@link DriftAdjustment} re-runs the step with the diff of what
 * failed injected (so the next attempt never receives the input the failed one did); once the
 * retries are spent, or when nothing can adjust, {@link HumanEscalation} refuses the turn with a
 * {@link PipelineShortCircuitException} — an output that did not pass is never served. Every
 * adjustment and escalation is recorded through the {@link ControlVerdictRecorder} (the outbox).
 *
 * <p><strong>Fails closed (sovkora-engine#125)</strong>: a streamed answer is screened too, the
 * deterministic checks on the chunks as they arrive ({@link #screenChunk}), every tier on the
 * assembled answer before the final event ({@link #screenStreamed}). A semantic judge (Tier C / D)
 * that times out, fails or answers nothing readable is never a pass and never a neutral score: it
 * is behind a {@link JudgeGuard} (circuit breaker, timeout) and the {@link JudgeOutagePolicy}
 * (admin data) decides: escalate with a typed refusal (the default), or serve on the deterministic
 * tiers alone with a visible badge and a {@link Degraded} audit record.
 *
 * <p>Tier execution order and enabled state are loaded dynamically from the {@link
 * ValidationPipelineRepository} database configuration. If the repository is unavailable, falls
 * back to the static {@link ClosedLoopValidationProperties} configuration.
 *
 * @since 1.0.0
 */
public class ClosedLoopValidationInterceptor implements PromptContextInterceptor {

  private static final Logger logger =
      LoggerFactory.getLogger(ClosedLoopValidationInterceptor.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String CRITIC_SYSTEM =
      "You are a ruthless code and logic critic. Analyze the following AI response for factual"
          + " errors, logical inconsistencies, missing edge cases, and alignment violations against"
          + " the original user intent. Score from 0 (perfect) to 10 (critically flawed). Respond"
          + " with ONLY a JSON object: {\"score\": N, \"issues\": [\"...\"]}.";

  private static final String ADVOCATE_SYSTEM =
      "You are a constructive advocate defending the following AI response. Evaluate whether it"
          + " faithfully addresses the user's original intent, is structurally sound, and provides"
          + " actionable value. Score from 0 (no value) to 10 (excellent). Respond with ONLY a JSON"
          + " object: {\"score\": N, \"strengths\": [\"...\"]}.";

  /** The machine-readable refusal code of a {@link HumanEscalation}. */
  public static final String ESCALATION_REASON = "control_escalation";

  /** The machine-readable refusal code when the semantic judge is unavailable. */
  public static final String UNAVAILABLE_REASON = "control_verification_unavailable";

  private static final int MAX_REASON_LENGTH = 500;

  private static final String ADJUST_SYSTEM =
      "You are correcting your own previous answer. A check rejected it. Return ONLY the corrected"
          + " answer, in the same format as asked, with no commentary.";

  private final ClosedLoopValidationProperties properties;
  private final ProviderChatClient chatModel;
  private final ValidationPipelineRepository validationPipelineRepository;
  private final TestShaperPort testShaperPort;
  private final ControlVerdictRecorder verdictRecorder;
  private final Clock clock;
  private final JudgeGuard judgeGuard;
  private final JudgeOutagePolicies outagePolicies;
  private final CodeBlockCheck codeBlocks;

  /**
   * Constructs a ClosedLoopValidationInterceptor with full 4-tier support and no audit recorder.
   *
   * @param properties Configuration controlling which tiers are active (static fallback).
   * @param chatModel model port for Tier C debate and for adjusting a rejected answer (nullable —
   *     Tier C degrades gracefully, a rejected answer then escalates at once).
   * @param validationPipelineRepository Repository for dynamic tier configuration (nullable —
   *     degrades to static config).
   * @param testShaperPort Tier D assertion generator (nullable — Tier D degrades gracefully).
   */
  public ClosedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ProviderChatClient chatModel,
      ValidationPipelineRepository validationPipelineRepository,
      TestShaperPort testShaperPort) {
    this(
        properties,
        chatModel,
        validationPipelineRepository,
        testShaperPort,
        null,
        null,
        Clock.systemUTC());
  }

  /**
   * Constructs the full control loop.
   *
   * @param properties Configuration controlling which tiers are active (static fallback).
   * @param chatModel model port for Tier C and for adjusting a rejected answer (nullable).
   * @param validationPipelineRepository Repository for dynamic tier configuration (nullable).
   * @param testShaperPort Tier D assertion generator (nullable).
   * @param verdictRecorder Where adjustments and escalations are persisted (nullable — not
   *     persisted).
   * @param sandbox Tier B sandbox adding to the built-in structural crash-test (nullable).
   * @param clock The clock stamping the persisted verdicts.
   */
  public ClosedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ProviderChatClient chatModel,
      ValidationPipelineRepository validationPipelineRepository,
      TestShaperPort testShaperPort,
      ControlVerdictRecorder verdictRecorder,
      CodeSandboxPort sandbox,
      Clock clock) {
    this.properties =
        Objects.requireNonNull(properties, "ClosedLoopValidationProperties must not be null");
    this.chatModel = chatModel;
    this.validationPipelineRepository = validationPipelineRepository;
    this.testShaperPort = testShaperPort;
    this.verdictRecorder = verdictRecorder;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.judgeGuard = new JudgeGuard(properties, clock);
    this.outagePolicies = new JudgeOutagePolicies(properties, validationPipelineRepository);
    this.codeBlocks = new CodeBlockCheck(sandbox);
  }

  /** A control: whatever it did not anticipate stops the turn instead of serving it unchecked. */
  @Override
  public boolean failsClosed() {
    return true;
  }

  @Override
  public boolean isAiDependent() {
    return (properties.debateEnabled() && chatModel != null)
        || (properties.tdrEnabled() && testShaperPort != null);
  }

  @Override
  public String screenResponse(PromptContext context, String responseText) {
    return screen(context, responseText).text();
  }

  @Override
  public ScreenedResponse screen(PromptContext context, String responseText) {
    return control(context, responseText, true);
  }

  /**
   * The chunks have left: the assembled answer is accepted or refused, never adjusted. Every active
   * tier runs, the semantic ones included, before the final event.
   */
  @Override
  public ScreenedResponse screenStreamed(PromptContext context, String assembled) {
    return control(context, assembled, false);
  }

  /**
   * The deterministic checks that can already be made on a partial answer: a code block that is
   * closed must be valid. (Whole-answer checks, an unterminated fence and Tier A, wait for the
   * assembled text, and the sandbox and the judge for {@link #screenStreamed}.)
   */
  @Override
  public void screenChunk(PromptContext context, String textSoFar) {
    if (!properties.enabled()
        || textSoFar == null
        || !textSoFar.contains("```")
        || !resolveActiveSteps().contains(ValidationStepType.SANDBOX_B)) {
      // The tiers are the ones of the non-streamed path (the DB rows, then the properties): a tier
      // disabled there is skipped per chunk too (sovkora-engine#127).
      return;
    }
    Optional<String> issue = codeBlocks.failure(textSoFar, false);
    if (issue.isPresent()) {
      throw escalate(runIdOf(context), new Failure(ValidationStepType.SANDBOX_B, issue.get()), 1);
    }
  }

  private ScreenedResponse control(PromptContext context, String responseText, boolean adjustable) {
    if (!properties.enabled() || responseText == null || responseText.isBlank()) {
      return ScreenedResponse.plain(responseText);
    }

    List<ValidationStepType> activeSteps = resolveActiveSteps();
    if (activeSteps.isEmpty()) {
      logger.debug("Closed-loop validation — No active tiers resolved. Skipping validation.");
      return ScreenedResponse.plain(responseText);
    }

    String userPrompt = userPromptOf(context);
    String runId = runIdOf(context);
    String current = responseText;

    for (int attempt = 0; ; attempt++) {
      Failure failure;
      try {
        failure = firstFailure(userPrompt, current, activeSteps);
      } catch (JudgeUnavailableException outage) {
        failure = onJudgeOutage(runId, outage, userPrompt, current, activeSteps, attempt);
        if (failure == null) {
          return degraded(current, outage);
        }
      }
      if (failure == null) {
        logger.debug(
            "Closed-loop validation — {} on attempt {}.",
            new Validated(activeSteps).reason(),
            attempt + 1);
        return new ScreenedResponse(
            current, Map.of(ControlMetadata.VERDICT.key(), ControlState.VALIDATED.name()));
      }
      current = adjustOrEscalate(runId, userPrompt, current, failure, attempt, adjustable);
    }
  }

  /** The next candidate after a failed attempt, or the escalation that stops the turn. */
  private String adjustOrEscalate(
      String runId,
      String userPrompt,
      String current,
      Failure failure,
      int attempt,
      boolean adjustable) {
    logger.warn(
        "Tier {} — attempt {}/{}: {}",
        failure.step().name(),
        attempt + 1,
        properties.maxRetries() + 1,
        failure.issue());
    String adjusted =
        adjustable && attempt < properties.maxRetries()
            ? adjust(userPrompt, current, failure)
            : null;
    if (adjusted == null) {
      throw escalate(runId, failure, attempt + 1);
    }
    record(runId, new DriftAdjustment(failure.step(), failure.summary(), failure.issue(), attempt));
    return adjusted;
  }

  /**
   * The judge is unavailable. The policy (admin data) decides: escalate with the typed refusal, or
   * check the deterministic tiers alone.
   *
   * @return a deterministic failure to handle as any other, or {@code null} when the deterministic
   *     tiers pass and the answer is served degraded (audited)
   */
  private Failure onJudgeOutage(
      String runId,
      JudgeUnavailableException outage,
      String userPrompt,
      String candidate,
      List<ValidationStepType> activeSteps,
      int attempt) {
    logger.error(
        "Closed-loop validation — {} unavailable ({}).", outage.step().name(), outage.getMessage());
    if (outagePolicies.of(outage.step()) == JudgeOutagePolicy.ESCALATE) {
      record(
          runId,
          new HumanEscalation(
              outage.step(),
              outage.step().name() + " unavailable: " + outage.getMessage(),
              outage.getMessage(),
              attempt + 1));
      throw new PipelineShortCircuitException(
          getId(),
          UNAVAILABLE_REASON,
          "Verification unavailable (" + outage.step().name() + "): the answer was not served.",
          outage);
    }
    List<ValidationStepType> deterministic =
        activeSteps.stream().filter(ClosedLoopValidationInterceptor::isDeterministic).toList();
    Failure failure = firstFailure(userPrompt, candidate, deterministic);
    if (failure == null) {
      record(runId, new Degraded(deterministic, outage.step(), outage.getMessage()));
    }
    return failure;
  }

  private static ScreenedResponse degraded(String text, JudgeUnavailableException outage) {
    logger.warn("Closed-loop validation — served degraded: {} unavailable.", outage.step().name());
    return new ScreenedResponse(
        text,
        Map.of(
            ControlMetadata.VERDICT.key(),
            ControlState.DEGRADED.name(),
            ControlMetadata.BADGE.key(),
            ControlBadge.VERIFICATION_UNAVAILABLE.value()));
  }

  private static boolean isDeterministic(ValidationStepType step) {
    return step == ValidationStepType.STRUCTURAL_A || step == ValidationStepType.SANDBOX_B;
  }

  /** Records the escalation and builds the refusal that stops the turn. */
  private PipelineShortCircuitException escalate(String runId, Failure failure, int attempts) {
    record(
        runId, new HumanEscalation(failure.step(), failure.summary(), failure.issue(), attempts));
    return new PipelineShortCircuitException(
        getId(),
        ESCALATION_REASON,
        "The answer could not be validated (" + failure.step().name() + ") and was not served.",
        null);
  }

  /** The first failing tier, in order, or {@code null} when every active tier passes. */
  private Failure firstFailure(
      String promptText, String candidate, List<ValidationStepType> activeSteps) {
    boolean tierAPass = true;
    boolean tierBPass = true;

    for (ValidationStepType step : activeSteps) {
      Optional<String> issue =
          switch (step) {
            case STRUCTURAL_A -> {
              Optional<String> result = executeTierA(candidate);
              tierAPass = result.isEmpty();
              yield result;
            }
            case SANDBOX_B -> {
              Optional<String> result = executeTierB(candidate);
              tierBPass = result.isEmpty();
              yield result;
            }
            case SEMANTIC_C -> executeTierC(promptText, candidate);
            case TDR_D -> executeTierD(promptText, candidate, tierAPass, tierBPass);
          };
      if (issue.isPresent()) {
        return new Failure(step, issue.get());
      }
    }
    return null;
  }

  /**
   * Re-runs the step with the diff of what failed injected. The prompt of the new attempt carries
   * the previous answer and the diff, so it differs from the one that failed.
   *
   * @return the adjusted answer, or {@code null} when nothing can adjust it (no model, a failure,
   *     an empty answer)
   */
  private String adjust(String userPrompt, String previous, Failure failure) {
    if (chatModel == null) {
      return null;
    }
    try {
      String instruction =
          "Original request:\n"
              + userPrompt
              + "\n\nYour previous answer:\n"
              + previous
              + "\n\nIt failed the "
              + failure.step().name()
              + " check:\n"
              + failure.issue()
              + "\n\nCorrect it.";
      String text =
          chatModel.completeWithDefault(
              List.of(EngineMessage.system(ADJUST_SYSTEM), EngineMessage.user(instruction)), null);
      return text == null || text.isBlank() ? null : text;
    } catch (RuntimeException e) {
      logger.error("Closed-loop validation — the adjustment attempt failed; escalating.", e);
      return null;
    }
  }

  private void record(String runId, ControlVerdict verdict) {
    if (verdictRecorder != null) {
      verdictRecorder.record(ControlVerdictRecord.of(runId, verdict, clock.instant()));
    }
  }

  private static String userPromptOf(PromptContext context) {
    if (context == null) {
      return "";
    }
    String refined = context.refinedPrompt();
    return refined != null ? refined : String.valueOf(context.rawUserQuery());
  }

  private static String runIdOf(PromptContext context) {
    if (context != null) {
      for (String key : List.of("runId", "conversationId")) {
        Object value = context.userMetadata().get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
          return String.valueOf(value);
        }
      }
    }
    return "unknown";
  }

  /** A tier that rejected a candidate, and why. */
  private record Failure(ValidationStepType step, String issue) {
    String summary() {
      String text = step.name() + ": " + issue;
      return text.length() > MAX_REASON_LENGTH ? text.substring(0, MAX_REASON_LENGTH) : text;
    }
  }

  /**
   * Resolves the ordered list of active validation steps. Prefers database configuration; falls
   * back to static properties if the repository is unavailable.
   */
  private List<ValidationStepType> resolveActiveSteps() {
    if (validationPipelineRepository != null) {
      try {
        List<ValidationPipelineConfiguration> dbConfigs =
            validationPipelineRepository.findAllOrderedByExecution();
        if (!dbConfigs.isEmpty()) {
          return dbConfigs.stream()
              .filter(ValidationPipelineConfiguration::enabled)
              .map(ValidationPipelineConfiguration::stepType)
              .toList();
        }
      } catch (RuntimeException e) {
        logger.warn("Failed to load validation pipeline from database — using static config.", e);
      }
    }

    // Static fallback from properties
    List<ValidationStepType> steps = new ArrayList<>();
    if (properties.schemaStrict()) {
      steps.add(ValidationStepType.STRUCTURAL_A);
    }
    if (properties.sandboxEnabled()) {
      steps.add(ValidationStepType.SANDBOX_B);
    }
    if (properties.debateEnabled()) {
      steps.add(ValidationStepType.SEMANTIC_C);
    }
    if (properties.tdrEnabled()) {
      steps.add(ValidationStepType.TDR_D);
    }
    return List.copyOf(steps);
  }

  /**
   * Tier B — crash-test of the code blocks. Every fenced block must be closed; a {@code json} block
   * must parse; a wired {@link CodeSandboxPort} compiles or lints each block as well. An answer
   * with no code block has nothing to crash-test. A sandbox that itself fails rejects the answer
   * (fails closed).
   *
   * @param responseText The LLM response text.
   * @return Empty if every block passes; the first failure otherwise.
   */
  private Optional<String> executeTierB(String responseText) {
    return codeBlocks.failure(responseText, true);
  }

  /**
   * Tier A — Deterministic JSON Schema validation.
   *
   * <p>Attempts to parse the response as JSON. If the response is not valid JSON and strict mode is
   * enabled, returns a description of the parse failure. Zero token cost — pure Jackson parse.
   *
   * @param responseText The LLM response text.
   * @return Empty if valid or non-strict; description of the issue otherwise.
   */
  private Optional<String> executeTierA(String responseText) {
    if (!properties.schemaStrict()) {
      return Optional.empty();
    }

    // Attempt to extract JSON from the response (may be embedded in markdown fences)
    String jsonCandidate = extractJsonBlock(responseText);
    if (jsonCandidate == null) {
      return Optional.empty();
    }

    try {
      MAPPER.readTree(jsonCandidate);
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of("Invalid JSON structure: " + e.getMessage());
    }
  }

  /**
   * Tier C — Semantic Consensus Debate.
   *
   * <p>Spawns two temperature-0.0 LLM calls: a Critic and an Advocate. If the Critic's score
   * exceeds the Advocate's, the response is deemed misaligned.
   *
   * @param userPrompt The original user prompt for intent alignment.
   * @param responseText The LLM response to evaluate.
   * @return Empty if Advocate prevails; critique summary if Critic prevails.
   */
  private Optional<String> executeTierC(String userPrompt, String responseText) {
    if (!properties.debateEnabled() || chatModel == null) {
      return Optional.empty();
    }

    String debateContext =
        "Original user request: " + userPrompt + "\n\nAI Response:\n" + responseText;

    // Critic and Advocate are one judge pass: one timeout, one breaker event.
    String[] verdicts =
        judgeGuard.call(
            ValidationStepType.SEMANTIC_C,
            () ->
                new String[] {
                  chatModel.completeWithDefault(
                      List.of(
                          EngineMessage.system(CRITIC_SYSTEM), EngineMessage.user(debateContext)),
                      0.0),
                  chatModel.completeWithDefault(
                      List.of(
                          EngineMessage.system(ADVOCATE_SYSTEM), EngineMessage.user(debateContext)),
                      0.0)
                });
    int criticScore = extractScore(verdicts[0]);
    int advocateScore = extractScore(verdicts[1]);

    logger.debug(
        "Tier C Debate — Critic: {}, Advocate: {} (Critic wins if Critic > Advocate)",
        criticScore,
        advocateScore);

    return criticScore > advocateScore ? Optional.of(verdicts[0]) : Optional.empty();
  }

  /**
   * Tier D — Test-Driven Response (TDR) validation.
   *
   * <p>Generates assertion contracts via the {@link TestShaperPort} and evaluates them against the
   * response. If Tier D assertions fail but Tiers A and B passed, gracefully delegates to Tier C
   * for final arbitration instead of immediate rejection.
   *
   * @param userPrompt The original user prompt.
   * @param responseText The LLM response to validate.
   * @param tierAPass Whether Tier A (structural) passed.
   * @param tierBPass Whether Tier B (sandbox) passed.
   * @return Empty if assertions pass; description of failures otherwise.
   * @throws JudgeUnavailableException when the assertion generator is unavailable (never a pass)
   */
  private Optional<String> executeTierD(
      String userPrompt, String responseText, boolean tierAPass, boolean tierBPass) {
    if (!properties.tdrEnabled() || testShaperPort == null) {
      return Optional.empty();
    }

    AssertionContract contract =
        judgeGuard.call(
            ValidationStepType.TDR_D,
            () -> testShaperPort.generateAssertions(userPrompt, responseText));
    if (!contract.hasAssertions()) {
      logger.debug("Tier D — No assertions to check: the model found nothing to assert.");
      return Optional.empty();
    }

    List<String> failures =
        contract.assertions().stream()
            .filter(assertion -> !evaluateAssertion(assertion, responseText))
            .toList();
    if (failures.isEmpty()) {
      logger.debug(
          "Tier D — All {} assertions passed for schema '{}'.",
          contract.assertions().size(),
          contract.schemaName());
      return Optional.empty();
    }

    // Graceful degradation: if A and B passed but D failed, delegate to C
    if (tierAPass && tierBPass && properties.debateEnabled() && chatModel != null) {
      logger.info(
          "Tier D — {} assertion(s) failed but Tiers A+B passed. "
              + "Delegating to Tier C for final arbitration.",
          failures.size());
      return executeTierC(userPrompt, responseText);
    }

    return Optional.of(
        "TDR assertion failures [" + contract.schemaName() + "]: " + String.join("; ", failures));
  }

  /**
   * Evaluates a single text-based assertion against the response. Uses simple keyword/phrase
   * matching. Returns true if the assertion is considered satisfied.
   */
  private boolean evaluateAssertion(String assertion, String responseText) {
    String lowerAssertion = assertion.toLowerCase();
    String lowerResponse = responseText.toLowerCase();

    // "must contain X" pattern
    if (lowerAssertion.contains("must contain")) {
      String target = extractTarget(lowerAssertion, "must contain");
      return target != null && lowerResponse.contains(target);
    }

    // "must not contain X" pattern
    if (lowerAssertion.contains("must not contain")) {
      String target = extractTarget(lowerAssertion, "must not contain");
      return target == null || !lowerResponse.contains(target);
    }

    // Default: soft pass for unrecognized assertion patterns
    return true;
  }

  /**
   * Extracts the target phrase after a given marker in an assertion string.
   *
   * @param assertion The assertion text (lowercase).
   * @param marker The marker to search after (e.g., "must contain").
   * @return The trimmed target phrase, or null if extraction fails.
   */
  private String extractTarget(String assertion, String marker) {
    int idx = assertion.indexOf(marker);
    if (idx == -1) {
      return null;
    }
    String tail = assertion.substring(idx + marker.length()).trim();
    // Strip leading articles
    if (tail.startsWith("a ")) {
      tail = tail.substring(2);
    }
    if (tail.startsWith("an ")) {
      tail = tail.substring(3);
    }
    return tail.isBlank() ? null : tail;
  }

  private String extractJsonBlock(String text) {
    return JsonBlocks.extract(text, true);
  }

  /**
   * Extracts the numeric "score" field from a debate response JSON.
   *
   * @param text The text answered by a Critic or Advocate call (nullable).
   * @return The score value.
   * @throws JudgeUnavailableException when there is no readable score: an unreadable judge is an
   *     outage, never a neutral 5
   */
  private int extractScore(String text) {
    String json = text == null ? null : extractJsonBlock(text);
    if (json == null) {
      throw unreadableScore(null);
    }
    try {
      JsonNode score = MAPPER.readTree(json).get("score");
      if (score == null || !score.isNumber()) {
        throw unreadableScore(null);
      }
      return score.asInt();
    } catch (JudgeUnavailableException unreadable) {
      throw unreadable;
    } catch (RuntimeException e) {
      throw unreadableScore(e);
    }
  }

  private static JudgeUnavailableException unreadableScore(Throwable origin) {
    return new JudgeUnavailableException(
        ValidationStepType.SEMANTIC_C, "the judge's answer holds no readable score", origin);
  }
}
