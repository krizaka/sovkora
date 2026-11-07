package com.krizaka.sovkora.jobs.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * What an executor produced, and what it measured producing it.
 *
 * <p>Two maps because they answer to two different readers and must not be confused. {@code output}
 * is the job's result, persisted and returned to whoever asked. {@code consumption} is raw
 * measurement for the settlement of the job's credit hold (ADR-033 §6.3).
 *
 * <p><b>Measurements, never billable units.</b> An executor reports frames, tokens, characters,
 * steps — quantities it actually observed. It never reports credits, and it never names a unit: the
 * unit belongs to the pricebook row the hold was pinned to, and an executor that priced itself
 * would be a second, drifting copy of the pricebook.
 *
 * <p>An empty {@code consumption} is meaningful rather than missing: an unmeasured job is
 * <b>released</b> rather than billed at its estimate. Reporting nothing costs the platform, which
 * is the right direction for the error to run.
 *
 * <p><b>{@code model} is what RAN, not what was asked for.</b> A producer sends a model or the
 * {@code "default"} sentinel; the executor is where that becomes a concrete name, and the pricebook
 * is keyed {@code (capability, model)}. Without it every studio step priced against its
 * capability's default row, which is wrong wherever one capability is served by engines that cost
 * different amounts — vision analysis and image generation are both IMAGE, and they are not the
 * same work (ADR-041). Reporting the name is not naming a price; the rate still lives in the
 * pricebook.
 *
 * @param output the job's result, persisted and relayed; defensively copied
 * @param consumption raw measurements keyed by name; defensively copied, may be empty
 * @param model the engine that produced this result, or {@code null} when the executor resolved
 *     none and the capability's default rate should apply
 */
public record JobExecutionResult(
    Map<String, Object> output, Map<String, Number> consumption, String model) {

  /** Compact canonical constructor enforcing the contract's invariants (ERR-106). */
  public JobExecutionResult {
    Objects.requireNonNull(output, "output must not be null");
    output = Collections.unmodifiableMap(Map.copyOf(output));
    consumption =
        consumption == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(Map.copyOf(consumption));
    // Blank is not a model: pinnedRate prefers a model-specific row and falls back to the
    // capability default only on null, so a blank would match neither.
    model = model == null || model.isBlank() ? null : model;
  }

  /**
   * A measured result from a named engine.
   *
   * @param output the job's result
   * @param consumption the raw measurements
   * @param model the engine that ran
   * @return the result
   */
  public static JobExecutionResult measured(
      Map<String, Object> output, Map<String, Number> consumption, String model) {
    return new JobExecutionResult(output, consumption, model);
  }

  /**
   * A result that reports no measurement of its own.
   *
   * <p>The common case for an executor whose cost is wall-clock: the listener times every execution
   * regardless, so such an executor still settles honestly.
   *
   * @param output the job's result
   * @return the result, with empty consumption
   */
  public static JobExecutionResult of(Map<String, Object> output) {
    return new JobExecutionResult(output, Map.of(), null);
  }
}
