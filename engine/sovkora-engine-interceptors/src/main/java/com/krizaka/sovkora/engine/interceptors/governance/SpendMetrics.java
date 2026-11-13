package com.krizaka.sovkora.engine.interceptors.governance;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The provider-spend meters Grafana reads, named by function. Tags are product, capability, model
 * and scope — never an actor, a request or a key.
 *
 * <p>Prometheus names: {@code ai_cost_total}, {@code spend_month_usd}, {@code spend_cap_ratio},
 * {@code spend_cap_alert_total}, {@code spend_cap_refused_total}, {@code
 * spend_cap_downgraded_total}, {@code ai_cost_unpriced_total}, {@code spend_record_failed_total}.
 */
public class SpendMetrics {

  private static final String SCOPE = "scope";

  private final MeterRegistry registry;
  private final Map<String, AtomicReference<Double>> ratios = new ConcurrentHashMap<>();
  private final Map<String, AtomicReference<Double>> spends = new ConcurrentHashMap<>();

  public SpendMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** The cost of one turn, once its tokens are priced. */
  public void cost(SpendScope scope, String capability, String model, BigDecimal usd) {
    registry
        .counter("ai.cost", "product", scope.productKey(), "capability", capability, "model", model)
        .increment(usd.doubleValue());
  }

  /** The month's spend of a scope and how much of its cap that is. */
  public void level(SpendScope scope, BigDecimal spentUsd, double ratio) {
    gauge("spend.month.usd", spends, scope).set(spentUsd.doubleValue());
    gauge("spend.cap.ratio", ratios, scope).set(ratio);
  }

  /** An alert threshold crossed, counted once per scope, month and threshold. */
  public void alert(SpendScope scope, int percent) {
    registry
        .counter("spend.cap.alert", SCOPE, scope.label(), "threshold", Integer.toString(percent))
        .increment();
  }

  /** A turn the cap stopped, by its typed reason. */
  public void refused(SpendScope scope, String reason) {
    registry.counter("spend.cap.refused", SCOPE, scope.label(), "reason", reason).increment();
  }

  /** A turn the cap served on the cheaper model. */
  public void downgraded(SpendScope scope) {
    registry.counter("spend.cap.downgraded", SCOPE, scope.label()).increment();
  }

  /** A turn on a model with no price row: its cost is unknown, and that is visible. */
  public void unpriced(String model) {
    registry.counter("ai.cost.unpriced", "model", model).increment();
  }

  /** A turn whose cost could not be written to the ledger. */
  public void recordFailed(SpendScope scope) {
    registry.counter("spend.record.failed", SCOPE, scope.label()).increment();
  }

  private AtomicReference<Double> gauge(
      String name, Map<String, AtomicReference<Double>> store, SpendScope scope) {
    return store.computeIfAbsent(
        scope.label(),
        label -> {
          AtomicReference<Double> value = new AtomicReference<>(0.0);
          Gauge.builder(name, value, AtomicReference::get)
              .tag(SCOPE, label)
              .strongReference(true)
              .register(registry);
          return value;
        });
  }
}
