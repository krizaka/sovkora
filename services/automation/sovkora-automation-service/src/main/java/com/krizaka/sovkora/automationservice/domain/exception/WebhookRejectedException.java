package com.krizaka.sovkora.automationservice.domain.exception;

import com.krizaka.sovkora.automationservice.domain.model.Rejection;
import java.io.Serial;
import java.util.UUID;

/** An inbound webhook call was refused before any work was done for it. */
public class WebhookRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient Rejection rejection;
  private final long retryAfterSeconds;
  private final transient UUID existingRunId;

  /**
   * @param rejection why
   * @param retryAfterSeconds when to come back, 0 when it is not a matter of time
   * @param existingRunId the run a replayed delivery first started, or null
   */
  public WebhookRejectedException(Rejection rejection, long retryAfterSeconds, UUID existingRunId) {
    super(rejection.code());
    this.rejection = rejection;
    this.retryAfterSeconds = retryAfterSeconds;
    this.existingRunId = existingRunId;
  }

  /**
   * Why the call was refused.
   *
   * @return the reason
   */
  public Rejection rejection() {
    return rejection;
  }

  /**
   * When to come back.
   *
   * @return seconds, 0 when not applicable
   */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }

  /**
   * The run a replayed delivery first started.
   *
   * @return the run id, or null
   */
  public UUID existingRunId() {
    return existingRunId;
  }
}
