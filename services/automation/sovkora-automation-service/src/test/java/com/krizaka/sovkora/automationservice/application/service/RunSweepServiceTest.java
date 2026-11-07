package com.krizaka.sovkora.automationservice.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.RunningStudioRun;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.StudioRunStarter;
import com.krizaka.sovkora.automationservice.domain.port.WebhookCredentialStore;
import com.krizaka.sovkora.automationservice.domain.port.WebhookRunLedger;
import com.krizaka.sovkora.automationservice.support.FixedClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunSweepServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  private final ScheduleClaimService schedules = mock(ScheduleClaimService.class);
  private final RunDispatchService dispatch = mock(RunDispatchService.class);
  private final RunRepository runs = mock(RunRepository.class);
  private final WebhookCredentialStore triggers = mock(WebhookCredentialStore.class);
  private final WebhookRunLedger ledger = mock(WebhookRunLedger.class);
  private final StudioRunStarter studio = mock(StudioRunStarter.class);
  private final AutomationSettingsService settings = mock(AutomationSettingsService.class);
  private RunSweepService sweep;

  @BeforeEach
  void setUp() {
    sweep =
        new RunSweepService(
            schedules, dispatch, runs, triggers, ledger, studio, settings, new FixedClock(NOW));
    for (SettingKey key : SettingKey.values()) {
      when(settings.value(key)).thenReturn(key.defaultValue());
    }
  }

  @Test
  @DisplayName(
      "a pass fires the due slots, starts the stale queued runs and forgets what is past retention")
  void pass() {
    UUID fromSchedule = UUID.randomUUID();
    UUID stale = UUID.randomUUID();
    when(schedules.claimDue()).thenReturn(List.of(fromSchedule));
    when(runs.staleQueued(any(), anyInt())).thenReturn(List.of(stale));

    sweep.sweep();

    verify(dispatch).dispatch(fromSchedule);
    verify(dispatch).dispatch(stale);
    verify(runs).staleQueued(NOW.minus(Duration.ofSeconds(30)), 20);
    verify(triggers).clearExpiredPreviousSecrets(NOW);
    verify(ledger).purgeDeliveries(NOW.minus(Duration.ofHours(24)));
  }

  @Test
  @DisplayName("a running run whose end event never came is closed from the Studio run itself")
  void reconciles() {
    UUID run = UUID.randomUUID();
    UUID studioRun = UUID.randomUUID();
    UUID stillGoing = UUID.randomUUID();
    when(runs.staleRunning(any(), anyInt()))
        .thenReturn(
            List.of(
                new RunningStudioRun(run, studioRun, "owner-1"),
                new RunningStudioRun(UUID.randomUUID(), stillGoing, "owner-1")));
    when(studio.endedStatus("owner-1", studioRun)).thenReturn(Optional.of(RunStatus.FAILED));
    when(studio.endedStatus("owner-1", stillGoing)).thenReturn(Optional.empty());

    sweep.sweep();

    verify(runs).finishByStudioRun(studioRun, RunStatus.FAILED, "studio_run_failed");
    verify(runs, never())
        .finishByStudioRun(org.mockito.ArgumentMatchers.eq(stillGoing), any(), any());
  }
}
