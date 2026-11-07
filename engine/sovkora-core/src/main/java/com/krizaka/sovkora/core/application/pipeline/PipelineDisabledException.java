package com.krizaka.sovkora.core.application.pipeline;

import java.io.Serial;

/**
 * Thrown when an engine turn arrives while the master orchestration switch is off.
 *
 * <p><b>A refusal by the operator, not by a gate, and typed apart from both.</b> Until ADR-062 the
 * switch returned {@code null} from the pipeline and the engine went on to call the model with the
 * raw prompt — skipping the crisis guard, the scope guard, the entitlement check, the credit hold
 * and the {@code disable-ai} gate, which is only enforced inside the pipeline. It could not be set
 * to {@code false}, so none of that ever happened; repairing the binding alone would have armed it.
 * Off now means no turn runs.
 *
 * <p>Not a {@link PipelineShortCircuitException}: that type means "a gate declined this request",
 * which job-service records as {@code GUARD_REFUSAL} and settles the work measured so far, and
 * which the chat API answers with {@code 403 upgrade_plan}. An operator switching the engine off is
 * neither the user's fault nor billable, so this type reads as {@code PLATFORM_UNAVAILABLE} and
 * {@code 503} (ADR-053).
 */
public class PipelineDisabledException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the refusal. The message names the switch, so a log line says which one was off. */
  public PipelineDisabledException() {
    super("engine turns are refused: orazaka.core.orchestration.enabled is false");
  }
}
