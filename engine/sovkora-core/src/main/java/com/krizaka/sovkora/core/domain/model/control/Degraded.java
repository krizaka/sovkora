package com.krizaka.sovkora.core.domain.model.control;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import java.util.List;

/**
 * The semantic judge was unavailable and the policy ({@link JudgeOutagePolicy#DETERMINISTIC_ONLY})
 * allows serving on the deterministic tiers alone. The output is servable only with a visible badge
 * and the run is audited; it is never a {@link Validated}.
 *
 * @param checked the deterministic tiers that ran and passed, in order
 * @param unavailable the judge tier that could not run
 * @param cause why it could not run (timeout, outage, open circuit breaker)
 */
public record Degraded(
    List<ValidationStepType> checked, ValidationStepType unavailable, String cause)
    implements ControlVerdict {

  /** Defensive copy. */
  public Degraded {
    checked = List.copyOf(checked);
  }

  @Override
  public String reason() {
    return unavailable.name() + " unavailable (" + cause + "): served on the deterministic tiers";
  }

  @Override
  public String actionTaken() {
    return "DEGRADED";
  }
}
