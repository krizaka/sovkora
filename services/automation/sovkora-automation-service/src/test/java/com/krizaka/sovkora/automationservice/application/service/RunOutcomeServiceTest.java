package com.krizaka.sovkora.automationservice.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunOutcomeServiceTest {

  private final RunRepository runs = mock(RunRepository.class);
  private final RunOutcomeService outcomes = new RunOutcomeService(runs);

  @Test
  @DisplayName(
      "a Studio run that succeeded closes its run as succeeded; one that failed, as failed")
  void closes() {
    UUID ok = UUID.randomUUID();
    UUID ko = UUID.randomUUID();
    outcomes.studioRunEnded(ok, true);
    outcomes.studioRunEnded(ko, false);
    verify(runs).finishByStudioRun(ok, RunStatus.SUCCEEDED, null);
    verify(runs).finishByStudioRun(ko, RunStatus.FAILED, "studio_run_failed");
  }
}
