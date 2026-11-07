package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.AssertionContract;
import com.krizaka.sovkora.core.domain.model.control.JudgeUnavailableException;

/**
 * Outbound port interface defining the contract for Tier D (Test-Driven Response) assertion
 * generation.
 *
 * <p>Implemented by a concrete adapter in {@code sovkora-interceptor-validation} that spins up a
 * fast reasoning model (e.g., Qwen-2.5-Coder) to generate structured test assertion schemas. The
 * generated {@link AssertionContract} is then used by the {@code ClosedLoopValidationInterceptor}
 * to validate LLM responses.
 *
 * @since 1.1.0
 */
public interface TestShaperPort {

  /**
   * Generates a set of test assertions based on the user's original prompt and the LLM response.
   *
   * <p>The implementation should analyze the prompt intent and produce machine-evaluable assertion
   * rules that the response must satisfy. A model that is down, silent or unreadable is not "no
   * assertions": implementations throw {@link JudgeUnavailableException}, so the control loop never
   * treats an unchecked answer as a checked one (sovkora-engine#125). {@link
   * AssertionContract#empty()} means the model read the exchange and found nothing to assert.
   *
   * @param userPrompt The original user prompt providing intent context.
   * @param responseText The LLM-generated response to validate.
   * @return A structured assertion contract (possibly empty: nothing to assert).
   * @throws JudgeUnavailableException when the model cannot give a usable contract
   */
  AssertionContract generateAssertions(String userPrompt, String responseText);
}
