package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import java.util.Objects;

/**
 * Meters a grounded research call over the credit hold (ADR-087 §5): one {@code RESEARCH} operation
 * of unit {@code CALL}, priced by the pricebook row whose {@code model_name} is the provider key
 * ({@code law-corpus}), held before the model is asked, settled with {@code calls: 1}, released
 * when the call fails.
 *
 * <p>It fails closed like {@link CreditHoldCheckMeter}: no billable actor or no credits is the
 * port's {@link BudgetExceededException}, a billing outage propagates.
 *
 * @param billing the credit hold port
 */
public record CreditHoldResearchMeter(CreditAuthorizationClient billing) implements ResearchMeter {

  /** Requires the port. */
  public CreditHoldResearchMeter {
    Objects.requireNonNull(billing, "CreditAuthorizationClient cannot be null");
  }

  @Override
  public Hold hold(String runId, String actorId, String providerKey) {
    return CreditHold.take(
            billing, BillableCapability.RESEARCH, providerKey, actorId, runId, "research")
        .orElseThrow(
            () ->
                new BudgetExceededException(
                    "no billable actor, or the credits cannot hold one research call"));
  }

  @Override
  public String toString() {
    return "CreditHoldResearchMeter[RESEARCH]";
  }
}
