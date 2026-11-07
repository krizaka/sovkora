package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.judge.JudgeRequest;
import com.krizaka.sovkora.core.domain.model.judge.JudgeVerdict;

/**
 * Port (ADR-094 §4.3): the model-judged level-2 check of the control loop (sovkora-engine#114). It
 * always runs after the deterministic level 1 (ADR-092 §4 rule 6), never instead of it.
 */
public interface OutputJudge {

  /**
   * Judges an output against its question and context.
   *
   * @param request what to judge
   * @return the verdict, never {@code null}
   */
  JudgeVerdict judge(JudgeRequest request);
}
