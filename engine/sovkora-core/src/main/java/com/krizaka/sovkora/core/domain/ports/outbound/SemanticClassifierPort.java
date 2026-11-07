package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.ClassificationResponse;

/**
 * Outbound port for semantic intent classification of user prompts.
 *
 * <p>Implementations connect to AI models (like LocalAI) to evaluate a prompt and return matched
 * routing intents. This port decouples the core semantic routing logic from HTTP clients.
 */
public interface SemanticClassifierPort {

  /**
   * Classifies the given prompt and returns the classification response.
   *
   * @param prompt The raw user prompt text to classify.
   * @return The matched intent labels above their respective confidence thresholds, or an empty
   *     list if classification fails.
   */
  ClassificationResponse classify(String prompt);
}
