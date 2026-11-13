package com.krizaka.sovkora.engine.interceptors.governance;

import com.krizaka.sovkora.engine.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.engine.core.domain.model.chat.TokenUsage;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.slf4j.*;
import org.springframework.context.event.EventListener;

/**
 * The one writer of provider spend: when a chat turn completes and its tokens are known, prices
 * them from the price table and adds the cost to the scope's month ({@link SpendLedger#add}), then
 * fires the alert thresholds the month has crossed, once each.
 *
 * <p>Off the answer: a failure here is logged and counted ({@code spend_record_failed_total}), it
 * never loses the turn the user was already served. A turn whose provider reported no usage, or
 * whose model has no price row, adds nothing — and the second is counted ({@code
 * ai_cost_unpriced_total}), so a missing price is visible instead of silent.
 */
public class ProviderSpendListener {

  private static final Logger log = LoggerFactory.getLogger(ProviderSpendListener.class);
  private static final String CAPABILITY = "CHAT";
  private static final String PROVIDER_METADATA = "provider";

  private final SpendLedger ledger;
  private final SpendCapProperties properties;
  private final SpendMetrics metrics;
  private final Clock clock;

  public ProviderSpendListener(
      SpendLedger ledger, SpendCapProperties properties, SpendMetrics metrics, Clock clock) {
    this.ledger = ledger;
    this.properties = properties;
    this.metrics = metrics;
    this.clock = clock;
  }

  /**
   * Records the cost of a completed turn.
   *
   * @param event the completed turn, carrying its token usage and the provider that served it
   */
  @EventListener
  public void onChatCompleted(ChatCompletedEvent event) {
    TokenUsage usage = event.response().tokenUsage();
    if (!usage.reported()) {
      return;
    }
    SpendScope scope =
        SpendScope.of(properties.environment(), event.request().context().preferences());
    try {
      record(scope, String.valueOf(event.response().metadata().get(PROVIDER_METADATA)), usage);
    } catch (RuntimeException failure) {
      log.error(
          "Could not record the provider spend of a turn in scope {}", scope.label(), failure);
      metrics.recordFailed(scope);
    }
  }

  private void record(SpendScope scope, String model, TokenUsage usage) {
    Optional<ProviderPrice> price = ledger.priceOf(model);
    if (price.isEmpty()) {
      log.warn("No price row for model {}; its spend is not counted", model);
      metrics.unpriced(model);
      return;
    }
    BigDecimal cost = price.get().costOf(usage);
    metrics.cost(scope, CAPABILITY, model, cost);
    if (cost.signum() == 0) {
      return;
    }
    YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
    BigDecimal spent = ledger.add(scope, month, cost);
    ledger
        .capOf(scope)
        .ifPresent(
            cap -> {
              double ratio = cap.ratio(spent);
              metrics.level(scope, spent, ratio);
              fireCrossedAlerts(scope, month, ratio);
            });
  }

  private void fireCrossedAlerts(SpendScope scope, YearMonth month, double ratio) {
    for (int percent : ledger.alertThresholds()) {
      if (ratio * 100 >= percent && ledger.markAlertFired(scope, month, percent)) {
        metrics.alert(scope, percent);
        log.warn(
            "Provider spend of scope {} reached {} % of its monthly cap", scope.label(), percent);
      }
    }
  }
}
