package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.model.control.JudgeOutagePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for the Closed-loop validation Advisor.
 *
 * <p>Controls the autonomous 4-tier self-correction chain: Tier A (Deterministic JSON Schema), Tier
 * B (MCP Sandbox Crash-Test), Tier C (Semantic Consensus Debate), and Tier D (Test-Driven Response
 * via System MCP).
 *
 * @param enabled Whether the quantum validation chain is active.
 * @param maxRetries Maximum number of correction retries before yielding.
 * @param schemaStrict Whether Tier A enforces strict JSON schema compliance.
 * @param sandboxEnabled Whether Tier B (MCP sandbox compilation) is active.
 * @param debateEnabled Whether Tier C (Critic vs Advocate semantic debate) is active.
 * @param tdrEnabled Whether Tier D (Test-Driven Response assertion generation) is active.
 * @param judgeTimeout How long one semantic judge pass (Tier C / D) may take before it counts as an
 *     outage.
 * @param judgeBreakerThreshold Consecutive judge failures that open the circuit breaker.
 * @param judgeBreakerOpenFor How long the breaker stays open before one probe call.
 * @param onJudgeOutage The static fallback of the outage policy, used where the admin has not set
 *     {@code onJudgeOutage} on the step (never a silent pass: escalate by default).
 */
@ConfigurationProperties(prefix = "orazaka.interceptor.validation.closed-loop")
public record ClosedLoopValidationProperties(
    boolean enabled,
    int maxRetries,
    boolean schemaStrict,
    boolean sandboxEnabled,
    boolean debateEnabled,
    boolean tdrEnabled,
    Duration judgeTimeout,
    int judgeBreakerThreshold,
    Duration judgeBreakerOpenFor,
    JudgeOutagePolicy onJudgeOutage) {

  /** Judge timeout when none is configured. */
  // @fixed: a judge that has not answered in 20 s is treated as down
  public static final Duration DEFAULT_JUDGE_TIMEOUT = Duration.ofSeconds(20);

  /** Breaker open time when none is configured. */
  // @fixed: long enough to spare a down judge, short enough to recover within the minute
  public static final Duration DEFAULT_BREAKER_OPEN_FOR = Duration.ofSeconds(30);

  /** The tiers and the retry budget, with the default judge safeguards (escalate on outage). */
  public ClosedLoopValidationProperties(
      boolean enabled,
      int maxRetries,
      boolean schemaStrict,
      boolean sandboxEnabled,
      boolean debateEnabled,
      boolean tdrEnabled) {
    this(
        enabled,
        maxRetries,
        schemaStrict,
        sandboxEnabled,
        debateEnabled,
        tdrEnabled,
        null,
        0,
        null,
        null);
  }

  /** Canonical constructor with validation and defaults. */
  @ConstructorBinding
  public ClosedLoopValidationProperties {
    judgeTimeout = judgeTimeout == null ? DEFAULT_JUDGE_TIMEOUT : judgeTimeout;
    judgeBreakerThreshold = judgeBreakerThreshold <= 0 ? 3 : judgeBreakerThreshold;
    judgeBreakerOpenFor =
        judgeBreakerOpenFor == null ? DEFAULT_BREAKER_OPEN_FOR : judgeBreakerOpenFor;
    onJudgeOutage = onJudgeOutage == null ? JudgeOutagePolicy.ESCALATE : onJudgeOutage;
    if (maxRetries < 0) {
      maxRetries = 0;
    }
    if (maxRetries > 5) {
      maxRetries = 5;
    }
  }

  /** Default configuration — all tiers active (Tier D disabled by default), 3 retry budget. */
  public static ClosedLoopValidationProperties defaults() {
    return new ClosedLoopValidationProperties(true, 3, true, true, true, false);
  }
}
