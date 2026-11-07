package com.krizaka.sovkora.core.domain.model.control;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;

/**
 * A check failed and an attempt is left: the step runs again with the diff injected, so the next
 * attempt never receives the input the failed one did.
 *
 * @param level the tier that failed
 * @param reason why the output drifted
 * @param diff what failed, as injected into the next attempt
 * @param attempt the attempt that failed (0 for the first)
 */
public record DriftAdjustment(ValidationStepType level, String reason, String diff, int attempt)
    implements ControlVerdict {

  @Override
  public String actionTaken() {
    return "ADJUSTED";
  }
}
