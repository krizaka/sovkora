package com.krizaka.sovkora.core.domain.model;

/**
 * Enumeration of the 4-Tier Validation Matrix step types.
 *
 * <p>Each tier represents a distinct validation mechanism in the autonomous self-correction chain
 * managed by the {@code ClosedLoopValidationInterceptor}. Tiers execute in ascending {@link
 * #defaultOrder} unless overridden by admin configuration.
 *
 * @since 1.1.0
 */
public enum ValidationStepType {

  /**
   * Tier A — Deterministic JSON Schema validation. Zero-token cost. Uses Jackson {@code
   * ObjectMapper} for structural parse and instant retry on malformed JSON.
   */
  STRUCTURAL_A(1),

  /**
   * Tier B — MCP Sandbox Crash-Test. Zero-token cost. Extracts code blocks from the response and
   * delegates compilation/linting to an isolated MCP sandbox environment.
   */
  SANDBOX_B(2),

  /**
   * Tier C — Semantic Consensus Debate. Costs 2 LLM calls. Spawns Critic + Advocate personas at
   * {@code temperature: 0.0} and retries if the Critic score exceeds the Advocate score.
   */
  SEMANTIC_C(3),

  /**
   * Tier D — Test-Driven Response (TDR). Pre-generates test assertion schemas via a fast reasoning
   * model (e.g., Qwen-2.5-Coder) and validates the LLM response against them. If Tier D fails but
   * Tiers A and B pass, gracefully delegates to Tier C for final arbitration.
   */
  TDR_D(4);

  private final int defaultOrder;

  ValidationStepType(int defaultOrder) {
    this.defaultOrder = defaultOrder;
  }

  /**
   * Returns the default execution order for this tier.
   *
   * @return The default execution position in the validation pipeline.
   */
  public int defaultOrder() {
    return defaultOrder;
  }
}
