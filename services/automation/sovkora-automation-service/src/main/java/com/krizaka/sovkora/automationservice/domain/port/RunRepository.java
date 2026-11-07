package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.DispatchTarget;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.RunningStudioRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The life of a recorded run: counted against its day's budget, started, ended — or never started
 * and swept. Statements that must be atomic are single statements: no transaction is held while
 * another service is called (ADR-084 §6).
 */
public interface RunRepository {

  /**
   * How many runs an automation started since an instant.
   *
   * @param automationId the automation
   * @param since the start of the window
   * @return the count
   */
  int runsSince(UUID automationId, Instant since);

  /**
   * What it takes to start a recorded run.
   *
   * @param runId the run
   * @return the target, empty when the run or its automation is gone
   */
  Optional<DispatchTarget> dispatchTarget(UUID runId);

  /**
   * Records that the Studio run was accepted.
   *
   * @param runId the run
   * @param studioRunId the Studio run
   * @return true when the run was queued and is now running
   */
  boolean markRunning(UUID runId, UUID studioRunId);

  /**
   * Records that the run could not go on.
   *
   * @param runId the run
   * @param code a stable code
   * @param message the reason in a few words
   * @return true when a queued or running run was ended
   */
  boolean markFailed(UUID runId, String code, String message);

  /**
   * Ends the run waiting on a Studio run.
   *
   * @param studioRunId the Studio run
   * @param status {@link RunStatus#SUCCEEDED} or {@link RunStatus#FAILED}
   * @param errorCode a stable code for a failure, null for a success
   * @return true when a running run was ended
   */
  boolean finishByStudioRun(UUID studioRunId, RunStatus status, String errorCode);

  /**
   * Runs recorded but not started for a while.
   *
   * @param before the cut-off
   * @param limit the most to return
   * @return their ids, oldest first
   */
  List<UUID> staleQueued(Instant before, int limit);

  /**
   * Runs started but not ended for a while.
   *
   * @param before the cut-off
   * @param limit the most to return
   * @return the runs, oldest first
   */
  List<RunningStudioRun> staleRunning(Instant before, int limit);
}
