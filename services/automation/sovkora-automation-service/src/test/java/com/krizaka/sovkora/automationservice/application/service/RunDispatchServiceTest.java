package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.exception.StudioStartRefusedException;
import com.krizaka.sovkora.automationservice.domain.model.DispatchTarget;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.StudioRunStarter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunDispatchServiceTest {

  private final RunRepository runs = mock(RunRepository.class);
  private final StudioRunStarter starter = mock(StudioRunStarter.class);
  private final RunDispatchService dispatch = new RunDispatchService(runs, starter);
  private final UUID runId = UUID.randomUUID();

  private DispatchTarget target(RunStatus status) {
    return new DispatchTarget(runId, "owner-1", "compliance-check", Map.of("a", 1), status);
  }

  @Test
  @DisplayName(
      "a queued run starts the Studio run as its owner, with the run id as idempotency key")
  void starts() {
    UUID studioRun = UUID.randomUUID();
    when(runs.dispatchTarget(runId)).thenReturn(Optional.of(target(RunStatus.QUEUED)));
    when(starter.start("owner-1", "compliance-check", Map.of("a", 1), runId.toString()))
        .thenReturn(studioRun);

    dispatch.dispatch(runId);

    verify(runs).markRunning(runId, studioRun);
  }

  @Test
  @DisplayName("the engine's refusal ends the run with its code and words; the run is not retried")
  void refused() {
    when(runs.dispatchTarget(runId)).thenReturn(Optional.of(target(RunStatus.QUEUED)));
    when(starter.start(anyString(), anyString(), any(), anyString()))
        .thenThrow(
            new StudioStartRefusedException(
                "studio_refused_402", "the engine refused the run (402)"));

    dispatch.dispatch(runId);

    verify(runs).markFailed(runId, "studio_refused_402", "the engine refused the run (402)");
    verify(runs, never()).markRunning(any(), any());
  }

  @Test
  @DisplayName("a run already started, finished or gone is left alone")
  void idempotent() {
    when(runs.dispatchTarget(runId)).thenReturn(Optional.of(target(RunStatus.RUNNING)));
    dispatch.dispatch(runId);
    when(runs.dispatchTarget(runId)).thenReturn(Optional.empty());
    dispatch.dispatch(runId);
    verify(starter, never()).start(anyString(), anyString(), any(), anyString());
    assertThat(RunStatus.RUNNING.isFinal()).isFalse();
  }

  @Test
  @DisplayName("a very long refusal is cut before it is stored")
  void truncates() {
    when(runs.dispatchTarget(runId)).thenReturn(Optional.of(target(RunStatus.QUEUED)));
    when(starter.start(anyString(), anyString(), any(), anyString()))
        .thenThrow(new StudioStartRefusedException("studio_refused_400", "x".repeat(900)));
    dispatch.dispatch(runId);
    verify(runs).markFailed(runId, "studio_refused_400", "x".repeat(500));
  }
}
