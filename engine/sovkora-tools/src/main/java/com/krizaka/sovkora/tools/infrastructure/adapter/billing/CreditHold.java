package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One {@code CALL} held against the credits, then closed once: the ADR-033 hold then settle or
 * release, shared by the meter of an external check and the meter of a grounded research.
 *
 * <p>Settle reports the measurement, {@code calls: 1}, never a unit nor a price: the pricebook row
 * the hold was pinned to prices it. Its idempotency key is derived from the hold, so a retried
 * settle is the same ledger entry. A grant that reserved nothing (billing off, {@link
 * CreditHoldResponse#metered()} false) has no hold behind it: settle and release do nothing. A
 * shadow grant ({@code dryRun}) is a real hold and is closed like any other.
 *
 * <p>It holds no personal data: the hold id and the ledger's key prefix only, and it never logs.
 */
final class CreditHold implements CheckMeter.Hold, ResearchMeter.Hold, ChatTokenMeter.Hold {

  /** One call: the measurement a settled {@code CALL} reports. */
  private static final ConsumptionReport ONE_CALL =
      new ConsumptionReport(null, null, null, null, null, null, null, null, null, null, null, 1L);

  private final CreditAuthorizationClient billing;
  private final String holdId;
  private final String keyPrefix;
  private final boolean metered;
  private final long estimatedCredits;
  private final AtomicBoolean closed = new AtomicBoolean();

  private CreditHold(
      CreditAuthorizationClient billing,
      String holdId,
      String keyPrefix,
      boolean metered,
      long estimatedCredits) {
    this.billing = billing;
    this.estimatedCredits = estimatedCredits;
    this.holdId = holdId;
    this.keyPrefix = keyPrefix;
    this.metered = metered;
  }

  /**
   * Holds one call, or refuses it.
   *
   * @param billing the credit hold port
   * @param capability the billable capability of the call
   * @param provider the pricebook's {@code model_name}
   * @param actorId who is billed; with none, nothing is held: a call is never made unmetered
   * @param runId the run the hold is correlated on, or {@code null} to correlate on the actor
   * @param keyPrefix the prefix of the settle's idempotency key
   * @return the hold, or empty when there is no billable actor or the credits cannot hold the call
   *     (enforced refusal, or a grant that is not granted, which is released first)
   */
  static Optional<CreditHold> take(
      CreditAuthorizationClient billing,
      BillableCapability capability,
      String provider,
      String actorId,
      String runId,
      String keyPrefix) {
    if (actorId == null || actorId.isBlank()) {
      return Optional.empty();
    }
    String correlationId = runId == null || runId.isBlank() ? actorId : runId;
    CreditHoldResponse grant;
    try {
      // No job id: a run's job is settled by its own hold, and the ledger finds holds by job.
      // Zero credits: the ledger reserves the pricebook row's estimate, one CALL.
      grant =
          billing.hold(
              new CreditHoldCommand(actorId, capability, provider, correlationId, null, 0L));
    } catch (InsufficientCreditsException refused) {
      return Optional.empty();
    }
    if (!grant.granted()) {
      if (grant.metered()) {
        billing.release(grant.holdId(), "not granted");
      }
      return Optional.empty();
    }
    return Optional.of(
        new CreditHold(
            billing, grant.holdId(), keyPrefix, grant.metered(), grant.estimatedCredits()));
  }

  @Override
  public void settle() {
    if (metered && closed.compareAndSet(false, true)) {
      billing.settleMeasured(holdId, ONE_CALL, keyPrefix + ":" + holdId);
    }
  }

  /**
   * Settles the tokens of a chat call as a measurement. Tokens the provider did not report are not
   * zero: the hold is released instead of billing the estimate.
   */
  @Override
  public void settle(TokenUsage usage) {
    if (!usage.reported()) {
      release("no token usage reported");
      return;
    }
    if (metered && closed.compareAndSet(false, true)) {
      billing.settleMeasured(
          holdId,
          new ConsumptionReport(
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              (long) usage.totalTokens(),
              null),
          keyPrefix + ":" + holdId);
    }
  }

  @Override
  public long estimatedCredits() {
    return metered ? estimatedCredits : 0L;
  }

  @Override
  public void release(String reason) {
    if (metered && closed.compareAndSet(false, true)) {
      billing.release(holdId, reason);
    }
  }

  @Override
  public String toString() {
    return "CreditHold[" + keyPrefix + ":" + holdId + "]";
  }
}
