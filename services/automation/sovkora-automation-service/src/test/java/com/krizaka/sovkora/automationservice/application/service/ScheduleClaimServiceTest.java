package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.DueSchedule;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.ScheduleStore;
import com.krizaka.sovkora.automationservice.support.FixedClock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduleClaimServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:30Z");
  private static final ScheduleSpec NOON =
      new ScheduleSpec(LocalTime.NOON, ZoneOffset.UTC, Set.of());

  private final ScheduleStore triggers = mock(ScheduleStore.class);
  private final RunRepository runs = mock(RunRepository.class);
  private final AutomationSettingsService settings = mock(AutomationSettingsService.class);
  private final UUID triggerId = UUID.randomUUID();
  private final UUID automationId = UUID.randomUUID();
  private ScheduleClaimService claim;

  @BeforeEach
  void setUp() {
    claim = new ScheduleClaimService(triggers, runs, settings, new FixedClock(NOW));
    for (SettingKey key : SettingKey.values()) {
      when(settings.value(key)).thenReturn(key.defaultValue());
    }
  }

  private DueSchedule due(Instant at, CatchUp catchUp) {
    return new DueSchedule(triggerId, automationId, NOON, catchUp, at);
  }

  @Test
  @DisplayName("a slot that has come records a queued run and moves the trigger to tomorrow's slot")
  void fires() {
    when(triggers.lockDueSchedules(any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(due(Instant.parse("2026-10-20T12:00:00Z"), CatchUp.ONCE)));

    List<UUID> queued = claim.claimDue();

    assertThat(queued).hasSize(1);
    verify(triggers).recordRun(queued.getFirst(), automationId, triggerId, RunStatus.QUEUED);
    verify(triggers).advanceSchedule(triggerId, Instant.parse("2026-10-21T12:00:00Z"), NOW);
  }

  @Test
  @DisplayName("a long-missed slot runs once under catch-up 'once' and is skipped under 'skip'")
  void catchUp() {
    Instant longAgo = Instant.parse("2026-10-19T12:00:00Z");
    when(triggers.lockDueSchedules(any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(due(longAgo, CatchUp.ONCE)));
    assertThat(claim.claimDue()).hasSize(1);

    when(triggers.lockDueSchedules(any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(due(longAgo, CatchUp.SKIP)));
    assertThat(claim.claimDue()).isEmpty();
    verify(triggers).advanceSchedule(triggerId, Instant.parse("2026-10-21T12:00:00Z"), null);
  }

  @Test
  @DisplayName("an automation over its day's budget records a skipped run and starts nothing")
  void overBudget() {
    when(triggers.lockDueSchedules(any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of(due(Instant.parse("2026-10-20T12:00:00Z"), CatchUp.ONCE)));
    when(runs.runsSince(eq(automationId), any()))
        .thenReturn(SettingKey.MAX_RUNS_PER_DAY.defaultValue());

    assertThat(claim.claimDue()).isEmpty();
    verify(triggers)
        .recordRun(any(), eq(automationId), eq(triggerId), eq(RunStatus.SKIPPED_BUDGET));
    verify(triggers).advanceSchedule(triggerId, Instant.parse("2026-10-21T12:00:00Z"), NOW);
  }

  @Test
  @DisplayName("nothing due, nothing recorded")
  void nothingDue() {
    when(triggers.lockDueSchedules(any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of());
    assertThat(claim.claimDue()).isEmpty();
    verify(triggers, never()).recordRun(any(), any(), any(), any());
  }
}
