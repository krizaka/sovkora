package com.krizaka.sovkora.interceptor.governance;

import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * Closes the hold {@link EntitlementInterceptor} took, against the tokens the model actually
 * produced (ADR-033 §6.2).
 *
 * <p>The two halves of the synchronous protocol live side by side on purpose: the interceptor opens
 * the reservation before inference, this listener closes it after, and keeping them in one package
 * is what stops the settle half being forgotten when the gate is changed.
 *
 * <p>Off the response path by construction. Spring publishes {@code ChatCompletedEvent} after the
 * engine has already returned the answer, so a slow or failing ledger cannot delay the last token a
 * user sees — the rule the design states twice, and the reason settle is not part of the hold's
 * round trip.
 *
 * <p>Reports <b>measurements</b>, never a unit: whether chat bills in kilotokens is a property of
 * the pricebook row the hold was pinned to, and naming a unit here would break the day a model is
 * repriced. A turn whose provider reported no usage is released rather than billed at its estimate
 * — charging a guess would hide the reporting gap behind revenue.
 */
public class ChatSettlementListener {

  private static final Logger log = LoggerFactory.getLogger(ChatSettlementListener.class);

  private final CreditAuthorizationClient creditAuthorizationClient;

  public ChatSettlementListener(CreditAuthorizationClient creditAuthorizationClient) {
    this.creditAuthorizationClient =
        Objects.requireNonNull(
            creditAuthorizationClient, "CreditAuthorizationClient cannot be null");
  }

  /**
   * Settles or releases a completed turn.
   *
   * @param event the completed turn, carrying its reservation and token usage
   */
  @EventListener
  public void onChatCompleted(ChatCompletedEvent event) {
    if (!event.metered()) {
      return;
    }
    TokenUsage usage = event.response().tokenUsage();
    if (!usage.reported()) {
      log.warn(
          "Provider reported no token usage for hold {}; releasing rather than billing an estimate",
          event.holdId());
      creditAuthorizationClient.release(event.holdId(), "no token usage reported");
      return;
    }
    creditAuthorizationClient.settleMeasured(
        event.holdId(), report(usage), idempotencyKeyFor(event));
  }

  /** Raw measurement only — the ledger derives the quantity for the pinned rate's unit. */
  private static ConsumptionReport report(TokenUsage usage) {
    return new ConsumptionReport(
        null, null, null, null, null, null, null, null, null, (long) usage.totalTokens(), null);
  }

  /**
   * The replay guard. A turn settles once, and the hold is what identifies it: the ledger's unique
   * key rejects a second attempt under the same reservation, whatever causes the retry.
   */
  private static String idempotencyKeyFor(ChatCompletedEvent event) {
    return "chat:" + event.holdId();
  }
}
