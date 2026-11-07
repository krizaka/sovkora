package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import java.util.Objects;
import java.util.UUID;

/**
 * Meters an external check over the credit hold (krizaka/sovkora-packs#138): one {@code
 * EXTERNAL_CHECK} operation of unit {@code CALL}, priced by the pricebook row whose {@code
 * model_name} is the connector's provider ({@code mock} today), held before the provider is asked,
 * settled with {@code calls: 1} when it answered, released when it did not.
 *
 * <p>It fails closed: a call with no billable actor, or credits that cannot hold it, is the port's
 * {@link BudgetExhaustedException} before billing (or the provider) is asked further, and a billing
 * outage propagates (no call is made). With billing off the grant reserves nothing and the call
 * runs, as every metered capability does then.
 *
 * @param billing the credit hold port
 */
public record CreditHoldCheckMeter(CreditAuthorizationClient billing) implements CheckMeter {

  /** Requires the port. */
  public CreditHoldCheckMeter {
    Objects.requireNonNull(billing, "CreditAuthorizationClient cannot be null");
  }

  @Override
  public Hold hold(UUID runId, String actorId, String provider, CheckPurpose purpose) {
    String run = runId == null ? null : runId.toString();
    return CreditHold.take(
            billing, BillableCapability.EXTERNAL_CHECK, provider, actorId, run, "external-check")
        .orElseThrow(
            () ->
                new BudgetExhaustedException(
                    "no billable actor, or the credits cannot hold one " + purpose + " call"));
  }

  @Override
  public String toString() {
    return "CreditHoldCheckMeter[EXTERNAL_CHECK]";
  }
}
