package com.krizaka.sovkora.core.domain.model;

/**
 * Defines the routing strategy used by the DynamicPipelineExecutor to determine interceptor
 * execution order.
 *
 * <ul>
 *   <li>{@link #DETERMINISTIC} — Database-driven ordering via {@code PipelineConfigProvider}. Admin
 *       controls the exact sequence via the UI.
 *   <li>{@link #AGENTIC} — LLM-driven runtime sequence generation based on payload intent analysis.
 * </ul>
 */
public enum RoutingMode {

  /**
   * Database-driven interceptor sequencing. The admin explicitly defines and saves the execution
   * order via the pipeline configuration UI. Changes take effect on next pipeline cache eviction.
   */
  DETERMINISTIC("Database-driven deterministic ordering"),

  /**
   * LLM-driven dynamic sequencing. The model analyzes the incoming payload intent at runtime and
   * generates the optimal interceptor execution sequence.
   */
  AGENTIC("LLM-driven runtime sequence generation"),

  /**
   * Prompt-classified dynamic interceptor selection. The {@code SemanticRoutingEngine} evaluates
   * the incoming prompt via a fast local embedding pass or classification query (LocalAI port 8085)
   * and dynamically selects only the required Phase 2 interceptors.
   */
  SEMANTIC("Prompt-classified dynamic interceptor selection");

  private final String description;

  RoutingMode(String description) {
    this.description = description;
  }

  /**
   * Returns a human-readable description of this routing mode.
   *
   * @return The routing mode description.
   */
  public String description() {
    return description;
  }
}
