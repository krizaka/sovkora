package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter.BudgetExceededException;
import java.util.Objects;

/**
 * Meters the chat tokens of a grounded call over the credit hold: a {@code CHAT} hold pinned to the
 * pricebook row of the model, settled with the tokens the provider reported under the key {@code
 * chat:<holdId>}, the one the synchronous chat path uses, so a retried settle is the same ledger
 * entry. It fails closed like {@link CreditHoldResearchMeter}.
 *
 * @param billing the credit hold port
 */
public record CreditHoldChatTokenMeter(CreditAuthorizationClient billing)
    implements ChatTokenMeter {

  /** Requires the port. */
  public CreditHoldChatTokenMeter {
    Objects.requireNonNull(billing, "CreditAuthorizationClient cannot be null");
  }

  @Override
  public Hold hold(String runId, String actorId) {
    return CreditHold.take(billing, BillableCapability.CHAT, null, actorId, runId, "chat")
        .orElseThrow(
            () ->
                new BudgetExceededException(
                    "no billable actor, or the credits cannot hold the tokens of one call"));
  }

  @Override
  public String toString() {
    return "CreditHoldChatTokenMeter[CHAT]";
  }
}
