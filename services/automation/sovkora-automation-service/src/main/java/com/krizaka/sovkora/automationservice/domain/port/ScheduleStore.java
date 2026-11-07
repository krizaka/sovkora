package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.DueSchedule;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The clock side of schedule triggers: the slots that have come, and the runs they start. */
public interface ScheduleStore {

  /**
   * Takes the schedule triggers whose slot has come, locking them for the rest of the transaction
   * so that two instances never fire the same slot.
   *
   * @param now the instant
   * @param limit the most to take
   * @return the due schedules, oldest first
   */
  List<DueSchedule> lockDueSchedules(Instant now, int limit);

  /**
   * Moves a schedule to its next slot.
   *
   * @param triggerId the trigger
   * @param nextFireAt the next slot
   * @param firedAt when it fired, null when the slot was skipped
   */
  void advanceSchedule(UUID triggerId, Instant nextFireAt, Instant firedAt);

  /**
   * Records a run started by a schedule slot.
   *
   * @param runId the new run's id
   * @param automationId the automation
   * @param triggerId the trigger
   * @param status {@link RunStatus#QUEUED}, or {@link RunStatus#SKIPPED_BUDGET} when nothing runs
   */
  void recordRun(UUID runId, UUID automationId, UUID triggerId, RunStatus status);
}
