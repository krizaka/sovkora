package com.krizaka.sovkora.business.api;

/**
 * How a use-case resolves its execution plan: {@code DETERMINISTIC} runs a fixed workflow (DAG),
 * {@code AGENTIC} delegates planning to the core's agent loop (plan→act→observe). Business runs the
 * plan; the intelligence lives in the core.
 */
public enum PlanningMode {
  DETERMINISTIC,
  AGENTIC
}
