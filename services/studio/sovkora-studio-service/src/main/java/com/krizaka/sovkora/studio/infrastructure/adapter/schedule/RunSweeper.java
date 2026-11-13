package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import com.krizaka.sovkora.jobs.api.domain.model.FailureCause;
import com.krizaka.sovkora.studio.api.domain.model.RunStatus;
import com.krizaka.sovkora.studio.api.domain.model.RunStepStatus;
import com.krizaka.sovkora.studio.application.service.OutboxService;
import com.krizaka.sovkora.studio.application.service.RunAuditService;
import com.krizaka.sovkora.studio.application.service.RunSettlementService;
import com.krizaka.sovkora.studio.application.service.StepReissueService;
import com.krizaka.sovkora.studio.application.service.StudioRuntimeConfigService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes runs whose steps stopped reporting, and releases their holds.
 *
 * <p>ADR-034 names this the one thing that hurts if skipped. A crashed worker leaves a step {@code
 * RUNNING} forever; without this, the run never reaches a terminal state and its hold silently
 * freezes a paying actor's balance — a failure that appears in no happy-path test and that users
 * experience as "my credits vanished".
 *
 * <p>The timeout is a row, not a constant: the number that needs changing when the accelerator is
 * saturated must be changeable without a deploy.
 *
 * <p><b>One ceiling per lane</b> (ADR-067), because {@code started_at} is stamped when the step row
 * is INSERTED — at dispatch, before any worker takes the message — so <b>queue wait counts against
 * the deadline</b>. A single number had to be large enough not to reap a 67-second image that
 * queued and small enough to catch a 200 ms text step that hung, and it cannot be both. Today the
 * contradiction is invisible because there is no contention; once the queue genuinely fills, a
 * legitimately-queued image gets reaped — run FAILED, hold released, and the worker still burning
 * the accelerator for a run that is already dead. That is a correctness consequence, not a latency
 * one, which is why it is closed before M3 makes this the only path.
 */
@Component
public class RunSweeper {

  private static final Logger logger = LoggerFactory.getLogger(RunSweeper.class);

  private static final String INTERACTIVE_TIMEOUT_KEY = "run.step-timeout-seconds.interactive";
  private static final int INTERACTIVE_FALLBACK_SECONDS = 300;

  private static final String BATCH_TIMEOUT_KEY = "run.step-timeout-seconds.batch";
  private static final int BATCH_FALLBACK_SECONDS = 1800;

  /**
   * The longest a run may stay in flight, whatever its steps say (sovkora#266): a run that no step
   * is waiting on (a fan-out that expanded to nothing, a lost event) is invisible to the per-step
   * ceilings, and holds one of its actor's concurrent-run slots until someone cancels it. A run
   * waiting for a person (approval, input) is not in flight and is never reaped here.
   */
  private static final String RUN_MAX_KEY = "run.max-seconds";

  private static final int RUN_MAX_FALLBACK_SECONDS = 4 * 3600;

  /**
   * The most times a silent step's command is sent again before its run fails (ADR SOV-105 §2). A
   * row, bounded 0..5 by its own min and max; 0 turns the guard off.
   */
  private static final String MAX_REISSUES_KEY = "run.step-max-reissues";

  private static final int MAX_REISSUES_FALLBACK = 2;

  /** The lane a step that never declared one is judged by — the one that waits (ADR-067). */
  private static final String DEFAULT_LANE = "BATCH";

  private final JdbcTemplate jdbcTemplate;
  private final RunSettlementService settlementService;
  private final StudioRuntimeConfigService runtimeConfigService;

  private final RunAuditService runAuditService;
  private final StepReissueService reissueService;
  private final OutboxService outboxService;
  private final MeterRegistry meterRegistry;

  public RunSweeper(
      JdbcTemplate jdbcTemplate,
      RunSettlementService settlementService,
      StudioRuntimeConfigService runtimeConfigService,
      RunAuditService runAuditService,
      StepReissueService reissueService,
      OutboxService outboxService,
      MeterRegistry meterRegistry) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.settlementService = Objects.requireNonNull(settlementService, "settlement");
    this.runtimeConfigService = Objects.requireNonNull(runtimeConfigService, "config required");
    this.runAuditService = Objects.requireNonNull(runAuditService, "RunAuditService required");
    this.reissueService = Objects.requireNonNull(reissueService, "StepReissueService required");
    this.outboxService = Objects.requireNonNull(outboxService, "OutboxService required");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry required");
  }

  /** Fails runs whose steps exceeded the timeout, releasing every outstanding hold. */
  @Scheduled(fixedDelayString = "${orazaka.studio-service.sweeper.interval:60000}")
  @Transactional
  public void sweep() {
    int interactiveSeconds =
        runtimeConfigService.intValue(INTERACTIVE_TIMEOUT_KEY, INTERACTIVE_FALLBACK_SECONDS);
    int batchSeconds = runtimeConfigService.intValue(BATCH_TIMEOUT_KEY, BATCH_FALLBACK_SECONDS);
    int maxReissues =
        Math.max(0, runtimeConfigService.intValue(MAX_REISSUES_KEY, MAX_REISSUES_FALLBACK));

    // First the steps that still have re-issues: a lost message costs a delay, not a paid file.
    reissueService.reissueSilentSteps(interactiveSeconds, batchSeconds, maxReissues);

    // What is left past its ceiling has no re-issue left, or its command never reached the broker
    // (the newest copy is missing or still unpublished): the dispatch is lost.
    List<StaleRun> stale =
        jdbcTemplate.query(
            "SELECT DISTINCT r.id, r.hold_id, r.actor_id, r.studio_key, r.blueprint_version,"
                + " r.correlation_id, COALESCE(s.latency_class, ?) AS lane FROM studio_run r"
                + " JOIN studio_run_step s ON s.run_id = r.id"
                + " WHERE r.finished_at IS NULL AND s.status = ?"
                + " AND COALESCE(s.reissued_at, s.started_at) < now() - make_interval(secs => CASE"
                + "   WHEN COALESCE(s.latency_class, ?) = 'INTERACTIVE' THEN ? ELSE ? END)"
                + " AND (s.reissues >= ? OR COALESCE((SELECT o.published_at IS NULL"
                + "   FROM studio_outbox o WHERE o.message_id = s.job_id"
                + "   ORDER BY o.created_at DESC LIMIT 1), TRUE))",
            (rs, rowNum) ->
                new StaleRun(
                    rs.getObject("id", UUID.class),
                    rs.getString("hold_id"),
                    rs.getString("actor_id"),
                    rs.getString("studio_key"),
                    rs.getString("blueprint_version"),
                    rs.getString("correlation_id"),
                    rs.getString("lane")),
            DEFAULT_LANE,
            RunStepStatus.RUNNING.name(),
            DEFAULT_LANE,
            interactiveSeconds,
            batchSeconds,
            maxReissues);

    for (StaleRun run : stale) {
      int ceiling = "INTERACTIVE".equals(run.lane()) ? interactiveSeconds : batchSeconds;
      logger.warn(
          "Run {} has a {} step past the {}s ceiling — failing it and releasing its hold",
          run.id(),
          run.lane(),
          ceiling);
      // A step that was sent again and still said nothing is a dispatch the platform lost, not a
      // step that is slow: the executor is declared unreachable (ADR-053). With the guard off
      // (0 re-issues) the platform cannot tell the two apart and keeps the old answer.
      fail(
          run,
          "a " + run.lane() + " step exceeded the " + ceiling + "s ceiling and never reported back",
          "swept: step exceeded its ceiling",
          maxReissues > 0 ? FailureCause.PLATFORM_UNAVAILABLE : FailureCause.TIMEOUT,
          "step_ceiling");
    }

    sweepOverdueRuns();
  }

  /** Fails the runs that have been in flight longer than {@code run.max-seconds}. */
  private void sweepOverdueRuns() {
    int maxSeconds =
        Math.max(60, runtimeConfigService.intValue(RUN_MAX_KEY, RUN_MAX_FALLBACK_SECONDS));
    List<StaleRun> overdue =
        jdbcTemplate.query(
            "SELECT id, hold_id, actor_id, studio_key, blueprint_version, correlation_id"
                + " FROM studio_run"
                + " WHERE finished_at IS NULL AND status = ?"
                + " AND started_at < now() - make_interval(secs => ?)",
            (rs, rowNum) ->
                new StaleRun(
                    rs.getObject("id", UUID.class),
                    rs.getString("hold_id"),
                    rs.getString("actor_id"),
                    rs.getString("studio_key"),
                    rs.getString("blueprint_version"),
                    rs.getString("correlation_id"),
                    "RUN"),
            RunStatus.RUNNING.name(),
            maxSeconds);
    for (StaleRun run : overdue) {
      logger.warn(
          "Run {} has been in flight past {}s — failing it and releasing its hold",
          run.id(),
          maxSeconds);
      fail(
          run,
          "the run was still in flight after " + maxSeconds + "s and was closed",
          "swept: run exceeded its maximum duration",
          FailureCause.TIMEOUT,
          "run_max");
    }
  }

  private void fail(
      StaleRun run, String reason, String releaseNote, FailureCause cause, String bound) {
    // The sweeper is the fourth producer of a failed run, and under ADR-053 it declares its cause
    // like the other three: the platform lost track of work it had accepted, which releases the
    // hold and blames nobody.
    jdbcTemplate.update(
        "UPDATE studio_run SET status = ?, error_message = ?, failure_cause = ?,"
            + " finished_at = now() WHERE id = ? AND finished_at IS NULL",
        RunStatus.FAILED.name(),
        reason,
        cause.name(),
        run.id());
    jdbcTemplate.update(
        "UPDATE studio_run_step SET status = ?, failure_cause = ?, finished_at = now()"
            + " WHERE run_id = ? AND status IN (?, ?)",
        RunStepStatus.FAILED.name(),
        cause.name(),
        run.id(),
        RunStepStatus.PENDING.name(),
        RunStepStatus.RUNNING.name());

    // The whole point: a stranded run must not strand its credits with it.
    settlementService.releaseAbandoned(run.holdId(), releaseNote);
    // And a protected run that ended this way must appear in its own trail. Before ADR-053 a
    // swept SENSITIVE run left no audit row at all — the one terminal path that recorded
    // nothing, which is the hole a trail cannot have.
    runAuditService.recordFinished(run.id(), run.actorId(), run.studioKey(), false, cause);
    // The failure is announced the way every other failed run is, so what follows runs by their
    // events hears this one too.
    outboxService.append(
        run.id().toString(),
        "evt.studio.run.failed",
        Map.of(
            "runId", run.id().toString(),
            "actorId", run.actorId(),
            "studioKey", run.studioKey(),
            "blueprintVersion", run.blueprintVersion(),
            "correlationId", run.correlationId()));
    meterRegistry.counter("sovkora.studio.run.past_bound", "bound", bound).increment();
  }

  /** A run with a step that stopped reporting, and the lane whose ceiling it passed. */
  private record StaleRun(
      UUID id,
      String holdId,
      String actorId,
      String studioKey,
      String blueprintVersion,
      String correlationId,
      String lane) {}
}
