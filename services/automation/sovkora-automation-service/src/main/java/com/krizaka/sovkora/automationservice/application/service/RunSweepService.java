package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.RunningStudioRun;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.StudioRunStarter;
import com.krizaka.sovkora.automationservice.domain.port.WebhookCredentialStore;
import com.krizaka.sovkora.automationservice.domain.port.WebhookRunLedger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The periodic housekeeping of automations: fire the schedule slots that have come, start the runs
 * that were recorded but not started, read the Studio runs whose end event never came, and forget
 * what is past its retention. One pass is cheap and safe to repeat; each statement is its own short
 * transaction (ADR-084 §6).
 */
@Service
public class RunSweepService {

  private static final int BATCH = 20;

  private final ScheduleClaimService schedules;
  private final RunDispatchService dispatch;
  private final RunRepository runs;
  private final WebhookCredentialStore credentials;
  private final WebhookRunLedger ledger;
  private final StudioRunStarter studio;
  private final AutomationSettingsService settings;
  private final Clock clock;

  public RunSweepService(
      ScheduleClaimService schedules,
      RunDispatchService dispatch,
      RunRepository runs,
      WebhookCredentialStore credentials,
      WebhookRunLedger ledger,
      StudioRunStarter studio,
      AutomationSettingsService settings,
      Clock clock) {
    this.schedules = Objects.requireNonNull(schedules, "ScheduleClaimService cannot be null");
    this.dispatch = Objects.requireNonNull(dispatch, "RunDispatchService cannot be null");
    this.runs = Objects.requireNonNull(runs, "RunRepository cannot be null");
    this.credentials = Objects.requireNonNull(credentials, "WebhookCredentialStore cannot be null");
    this.ledger = Objects.requireNonNull(ledger, "WebhookRunLedger cannot be null");
    this.studio = Objects.requireNonNull(studio, "StudioRunStarter cannot be null");
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsService cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  /** Runs one pass. */
  public void sweep() {
    Instant now = clock.instant();
    schedules.claimDue().forEach(dispatch::dispatch);

    Instant queuedBefore =
        now.minus(Duration.ofSeconds(settings.value(SettingKey.RUN_QUEUED_STALE_SECONDS)));
    for (UUID runId : runs.staleQueued(queuedBefore, BATCH)) {
      dispatch.dispatch(runId);
    }

    Instant runningBefore =
        now.minus(Duration.ofSeconds(settings.value(SettingKey.RUN_RECONCILE_AFTER_SECONDS)));
    for (RunningStudioRun waiting : runs.staleRunning(runningBefore, BATCH)) {
      studio
          .endedStatus(waiting.ownerId(), waiting.studioRunId())
          .ifPresent(
              status ->
                  runs.finishByStudioRun(
                      waiting.studioRunId(),
                      status,
                      status == RunStatus.FAILED ? "studio_run_failed" : null));
    }

    credentials.clearExpiredPreviousSecrets(now);
    ledger.purgeDeliveries(
        now.minus(Duration.ofHours(settings.value(SettingKey.WEBHOOK_REPLAY_RETENTION_HOURS))));
  }
}
