package com.krizaka.sovkora.jobs.api.domain.exception;

import java.io.Serial;

/**
 * Thrown when a {@link com.krizaka.sovkora.jobs.api.domain.port.JobExecutor} cannot complete a job.
 *
 * <p>Checked on purpose. An executor failing is an ordinary, expected outcome — a missing input
 * file, a model that refused, an accelerator out of memory — and the listener must translate it
 * into a {@code job.{jobId}.error} event and a released hold. An unchecked exception invites a
 * caller to forget that path; a checked one does not (ADR-038).
 *
 * <p>Lives in Tier-1 rather than in the job service because it is part of the executor contract: an
 * out-of-tree executor has to be able to throw it without depending on the service that runs it.
 */
public class JobExecutionException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param message what failed, in terms the operator reading a failed job can act on
   */
  public JobExecutionException(String message) {
    super(message);
  }

  /**
   * @param message what failed
   * @param cause the underlying failure
   */
  public JobExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
