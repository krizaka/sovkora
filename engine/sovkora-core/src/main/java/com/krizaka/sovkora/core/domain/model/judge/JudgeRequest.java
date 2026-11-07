package com.krizaka.sovkora.core.domain.model.judge;

import java.util.List;
import java.util.Objects;

/**
 * What the level-2 judge reads (ADR-094 §4.3): the question asked, the answer to check and the
 * context the answer must be supported by (retrieved passages; empty when there is none).
 *
 * @param question what was asked
 * @param answer the answer to judge
 * @param contexts the passages the answer must be supported by
 */
public record JudgeRequest(String question, String answer, List<String> contexts) {

  /** Validates the parts and copies the contexts. */
  public JudgeRequest {
    Objects.requireNonNull(question, "question");
    Objects.requireNonNull(answer, "answer");
    contexts = List.copyOf(contexts);
  }
}
