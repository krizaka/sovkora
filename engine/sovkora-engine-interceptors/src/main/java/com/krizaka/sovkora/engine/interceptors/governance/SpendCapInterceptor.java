package com.krizaka.sovkora.engine.interceptors.governance;

import com.krizaka.sovkora.engine.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.engine.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.engine.core.domain.model.Context;
import com.krizaka.sovkora.engine.core.domain.model.PromptContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.*;

/**
 * Applies the monthly provider-spend cap of the turn's scope (environment and product key) before
 * the turn reaches a model.
 *
 * <p>The cap, its action and the month's spend are rows ({@link SpendLedger}); nothing here is an
 * amount. Under the cap the turn passes. At the cap the row decides: {@link SpendCapAction#REFUSE}
 * stops the turn with a typed refusal, {@link SpendCapAction#DOWNGRADE} routes it to the cheaper
 * chat model the row names and stamps {@value #DOWNGRADED_MARKER}, so the router that runs later
 * leaves the provider alone (declared, not inferred).
 *
 * <p><b>Failure posture: closed.</b> A scope with no cap row, or a counter that cannot be read,
 * stops the turn: a cap that cannot be checked is not a cap, and the cost of the other choice is
 * unbounded money. This is the opposite of the credit gate, which fails open and accounts for it:
 * what that gate's failure costs is margin on one turn, this one's is the month's bill.
 *
 * <p>The check is on the spend written so far: turns in flight when the cap is crossed finish, so a
 * cap can be overshot by what is already running, never by what starts after.
 */
public class SpendCapInterceptor implements PromptContextInterceptor {

  /** Refusal code: the scope's month is at its cap and the cap action is a refusal. */
  public static final String REASON_CAP_REACHED = "provider_spend_cap_reached";

  /** Refusal code: the cap or the counter could not be read, so the turn is not served. */
  public static final String REASON_COUNTER_UNAVAILABLE = "provider_spend_counter_unavailable";

  /** System-metadata marker: the cap already chose the provider of this turn. */
  public static final String DOWNGRADED_MARKER = Context.PLATFORM_NAMESPACE + "spend.downgraded";

  private static final Logger log = LoggerFactory.getLogger(SpendCapInterceptor.class);

  private final SpendLedger ledger;
  private final SpendCapProperties properties;
  private final SpendMetrics metrics;
  private final Clock clock;

  public SpendCapInterceptor(
      SpendLedger ledger, SpendCapProperties properties, SpendMetrics metrics, Clock clock) {
    this.ledger = ledger;
    this.properties = properties;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Override
  public PromptContext beforeExecution(PromptContext context) {
    SpendScope scope = SpendScope.of(properties.environment(), context.userMetadata());
    SpendCap cap = capOf(scope);
    BigDecimal spent = spentBy(scope);

    metrics.level(scope, spent, cap.ratio(spent));
    if (!cap.reachedBy(spent)) {
      return context;
    }
    if (cap.action() == SpendCapAction.REFUSE) {
      log.warn("Provider-spend cap reached for scope {}; the turn is refused", scope.label());
      throw refusal(scope, REASON_CAP_REACHED, "The monthly provider spend cap is reached");
    }
    log.warn(
        "Provider-spend cap reached for scope {}; the turn is served on {}",
        scope.label(),
        cap.downgradeModelKey());
    metrics.downgraded(scope);
    Map<String, Object> system = new HashMap<>(context.systemMetadata());
    system.put(DOWNGRADED_MARKER, true);
    return context
        .withRoutedProvider(cap.downgradeModelKey())
        .withSystemMetadata(Map.copyOf(system));
  }

  /** The scope's cap row; a scope with none, or a row that cannot be read, stops the turn. */
  private SpendCap capOf(SpendScope scope) {
    Optional<SpendCap> row;
    try {
      row = ledger.capOf(scope);
    } catch (RuntimeException unreadable) {
      throw unreadable(scope, unreadable);
    }
    if (row.isEmpty()) {
      log.error("No provider-spend cap row for scope {}; the turn is refused", scope.label());
      throw refusal(scope, REASON_COUNTER_UNAVAILABLE, "No spend cap is set for this scope");
    }
    return row.get();
  }

  /** What the scope has spent this calendar month; a counter that cannot be read stops the turn. */
  private BigDecimal spentBy(SpendScope scope) {
    try {
      return ledger.spentIn(scope, YearMonth.now(clock.withZone(ZoneOffset.UTC)));
    } catch (RuntimeException unreadable) {
      throw unreadable(scope, unreadable);
    }
  }

  private PipelineShortCircuitException unreadable(SpendScope scope, RuntimeException cause) {
    log.error("Provider-spend counter unreadable for scope {}", scope.label(), cause);
    return refusal(scope, REASON_COUNTER_UNAVAILABLE, "The spend counter cannot be read");
  }

  private PipelineShortCircuitException refusal(SpendScope scope, String reason, String message) {
    metrics.refused(scope, reason);
    return new PipelineShortCircuitException(getId(), reason, message, null);
  }

  /** The control reads rows and a counter; it calls no model. */
  @Override
  public boolean isAiDependent() {
    return false;
  }

  /** A control: whatever it did not anticipate stops the turn. */
  @Override
  public boolean failsClosed() {
    return true;
  }
}
