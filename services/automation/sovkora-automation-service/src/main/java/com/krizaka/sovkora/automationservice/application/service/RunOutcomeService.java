package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Closes the run that was waiting on a Studio run when the engine says it ended. */
@Service
public class RunOutcomeService {

  private final RunRepository runs;

  public RunOutcomeService(RunRepository runs) {
    this.runs = Objects.requireNonNull(runs, "RunRepository cannot be null");
  }

  /**
   * Records the end of a Studio run. A Studio run that no automation run waits on is ignored; so is
   * one that already ended, which makes a redelivered event harmless.
   *
   * @param studioRunId the Studio run
   * @param succeeded whether it succeeded
   */
  public void studioRunEnded(UUID studioRunId, boolean succeeded) {
    runs.finishByStudioRun(
        studioRunId,
        succeeded ? RunStatus.SUCCEEDED : RunStatus.FAILED,
        succeeded ? null : "studio_run_failed");
  }
}
