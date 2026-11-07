package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Objects;
import java.util.UUID;

/**
 * One external check asked for by an actor.
 *
 * @param userId the actor, whose own key is used when there is one; {@code null} for a platform
 *     call
 * @param runId the Studio run it serves, or {@code null}
 * @param subject the person checked
 * @param region the subject's region (e.g. {@code QC}), which routes the call; {@code null} when
 *     unknown
 */
public record CheckCall(String userId, UUID runId, CheckSubject subject, String region) {

  /**
   * A call with no region.
   *
   * @param userId the actor, or {@code null}
   * @param runId the run, or {@code null}
   * @param subject the person checked
   */
  public CheckCall(String userId, UUID runId, CheckSubject subject) {
    this(userId, runId, subject, null);
  }

  /** Validates the call. */
  public CheckCall {
    Objects.requireNonNull(subject, "subject must not be null");
  }
}
