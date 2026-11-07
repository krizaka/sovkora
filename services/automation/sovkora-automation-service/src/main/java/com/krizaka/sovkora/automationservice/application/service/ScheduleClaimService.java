package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.DueSchedule;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.ScheduleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes the schedule slots that have come: in one short transaction it locks the due triggers,
 * records a run for each slot that fires and moves the trigger to its next slot. Nothing remote is
 * called inside it (ADR-084 §4, §6); the caller starts the runs after the commit.
 */
@Service
public class ScheduleClaimService {

  private static final int BATCH = 20;

  private final ScheduleStore schedules;
  private final RunRepository runs;
  private final AutomationSettingsService settings;
  private final Clock clock;

  public ScheduleClaimService(
      ScheduleStore schedules,
      RunRepository runs,
      AutomationSettingsService settings,
      Clock clock) {
    this.schedules = Objects.requireNonNull(schedules, "ScheduleStore cannot be null");
    this.runs = Objects.requireNonNull(runs, "RunRepository cannot be null");
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsService cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  /**
   * Claims the slots that have come.
   *
   * @return the runs recorded as queued, to be started
   */
  @Transactional
  public List<UUID> claimDue() {
    Instant now = clock.instant();
    Duration late = Duration.ofSeconds(settings.value(SettingKey.SCHEDULE_LATE_TOLERANCE_SECONDS));
    Instant dayStart =
        now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    int dailyLimit = settings.value(SettingKey.MAX_RUNS_PER_DAY);
    List<UUID> queued = new ArrayList<>();
    for (DueSchedule due : schedules.lockDueSchedules(now, BATCH)) {
      Instant next = due.schedule().nextAfter(now);
      boolean missed = due.dueAt().plus(late).isBefore(now);
      if (missed && due.catchUp() == CatchUp.SKIP) {
        schedules.advanceSchedule(due.triggerId(), next, null);
        continue;
      }
      UUID runId = UUID.randomUUID();
      boolean overBudget = runs.runsSince(due.automationId(), dayStart) >= dailyLimit;
      schedules.recordRun(
          runId,
          due.automationId(),
          due.triggerId(),
          overBudget ? RunStatus.SKIPPED_BUDGET : RunStatus.QUEUED);
      schedules.advanceSchedule(due.triggerId(), next, now);
      if (!overBudget) {
        queued.add(runId);
      }
    }
    return queued;
  }
}
