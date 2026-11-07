package com.krizaka.sovkora.business.domain.port;

import com.krizaka.sovkora.business.domain.model.WorkflowContext;

/**
 * Business-owned port interface for sovereign workflow orchestration.
 *
 * <p>This port defines the contract between the Business layer (intentions, rules, policies) and
 * the infrastructure adapter that translates business context into Core pipeline execution. The
 * Business layer dictates <b>"What"</b> must be achieved via the {@link WorkflowContext}, while the
 * implementing adapter (in the Router module) handles <b>"How"</b> it maps to the Core engine's
 * infrastructure types.
 *
 * <p>Implementations must never leak Core infrastructure types ({@code Context}, {@code
 * ChatRequest}, {@code PromptContext}) back through this interface. The sole translation boundary
 * lives in the Router's {@code WorkflowAdapter}.
 *
 * @see WorkflowContext
 * @since 1.0.0
 */
public interface WorkflowOrchestrator {

  /**
   * Executes a sovereign workflow prompt using the provided business context.
   *
   * @param userId The authenticated user identifier driving the execution (no guest fallback).
   * @param userPrompt The raw user query to process.
   * @param workflowContext The rich business context carrying tier, interceptor policies, and
   *     metadata.
   * @return The LLM response text, or an empty string if inference produces no output.
   */
  String executeSovereignPrompt(String userId, String userPrompt, WorkflowContext workflowContext);
}
