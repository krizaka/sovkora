package com.krizaka.sovkora.core.domain.model.control;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import java.util.List;

/**
 * Every active check passed: the output may be served.
 *
 * @param checked the tiers that ran and passed, in order
 */
public record Validated(List<ValidationStepType> checked) implements ControlVerdict {

  /** Defensive copy. */
  public Validated {
    checked = List.copyOf(checked);
  }

  @Override
  public String reason() {
    return "all active checks passed";
  }

  @Override
  public String actionTaken() {
    return "VALIDATED";
  }
}
