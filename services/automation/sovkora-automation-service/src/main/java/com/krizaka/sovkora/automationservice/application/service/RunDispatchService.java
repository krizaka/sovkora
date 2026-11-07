package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.exception.StudioStartRefusedException;
import com.krizaka.sovkora.automationservice.domain.model.DispatchTarget;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.StudioRunStarter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Starts the Studio run of a recorded run, as the automation's owner. No transaction is open while
 * the engine is called (ADR-084 §6): the run row was committed before, and the outcome is written
 * after in one more statement. The run id is the idempotency key, so a start that is retried after
 * a crash cannot run twice.
 */
@Service
public class RunDispatchService {

  private static final Logger logger = LoggerFactory.getLogger(RunDispatchService.class);
  private static final int MESSAGE_MAX = 500;

  private final RunRepository runs;
  private final StudioRunStarter starter;

  public RunDispatchService(RunRepository runs, StudioRunStarter starter) {
    this.runs = Objects.requireNonNull(runs, "RunRepository cannot be null");
    this.starter = Objects.requireNonNull(starter, "StudioRunStarter cannot be null");
  }

  /**
   * Starts the run if it is still waiting to be started.
   *
   * @param runId the recorded run
   */
  public void dispatch(UUID runId) {
    Optional<DispatchTarget> target = runs.dispatchTarget(runId);
    if (target.isEmpty() || target.get().status() != RunStatus.QUEUED) {
      return;
    }
    DispatchTarget run = target.get();
    try {
      UUID studioRunId =
          starter.start(run.ownerId(), run.studioKey(), run.inputs(), runId.toString());
      runs.markRunning(runId, studioRunId);
    } catch (StudioStartRefusedException refused) {
      logger.info("run {} refused by the engine: {}", runId, refused.code());
      runs.markFailed(runId, refused.code(), truncate(refused.getMessage()));
    }
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() <= MESSAGE_MAX ? message : message.substring(0, MESSAGE_MAX);
  }
}
