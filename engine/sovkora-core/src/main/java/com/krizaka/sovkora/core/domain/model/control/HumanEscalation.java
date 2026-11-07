package com.krizaka.sovkora.core.domain.model.control;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;

/**
 * A check failed and no attempt is left (or the same failure came back): the output is never
 * served, a person decides.
 *
 * @param level the tier that failed last
 * @param reason why the output could not be validated
 * @param diff what failed
 * @param attempts how many attempts were made
 */
public record HumanEscalation(ValidationStepType level, String reason, String diff, int attempts)
    implements ControlVerdict {

  @Override
  public String actionTaken() {
    return "ESCALATED";
  }
}
