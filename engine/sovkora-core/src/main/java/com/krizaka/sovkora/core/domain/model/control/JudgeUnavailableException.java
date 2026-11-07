package com.krizaka.sovkora.core.domain.model.control;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import java.io.Serial;

/**
 * The semantic judge (Tier C / Tier D) could not give a usable answer: a timeout, an outage, an
 * open circuit breaker, an empty or unreadable reply (sovkora-engine#125). It is never a pass and
 * never a neutral score: the control loop applies the {@link JudgeOutagePolicy}.
 */
public class JudgeUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ValidationStepType step;

  /**
   * @param step the judge tier that could not run
   * @param cause why (timeout, outage, unreadable reply…)
   * @param origin the underlying failure, or {@code null}
   */
  public JudgeUnavailableException(ValidationStepType step, String cause, Throwable origin) {
    super(cause, origin);
    this.step = step;
  }

  /**
   * @return the judge tier that could not run
   */
  public ValidationStepType step() {
    return step;
  }
}
