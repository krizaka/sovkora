package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.checks.CheckCall;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.model.checks.CheckStepResult;

/**
 * The external checks as engine capabilities (sovkora-engine#84): a blueprint step names one by its
 * feature key ({@link CheckPurpose#featureKey()}: {@code orazaka.core.checks.courtrecords}, {@code
 * orazaka.core.checks.credit}, {@code orazaka.core.checks.identity}). Every call is consent-gated,
 * metered as one {@code CALL} and held against the run budget; the step reports {@code
 * consumption.calls}.
 */
public interface ExternalCheckCapability {

  /**
   * Runs the check a step names.
   *
   * @param featureKey the step's feature key
   * @param call the actor, the run and the subject
   * @return the result and its consumption
   * @throws IllegalArgumentException when the key names no external check
   */
  CheckStepResult run(String featureKey, CheckCall call);
}
