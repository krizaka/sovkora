package com.krizaka.sovkora.studio.application.service;

import com.krizaka.sovkora.jobs.api.domain.model.FailureCause;
import com.krizaka.sovkora.studio.api.domain.model.RunStatus;
import com.krizaka.sovkora.studio.infrastructure.support.RunMeasureRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends the runs that never dispatched a step (krizaka-internal#263).
 *
 * <p>A run {@code RUNNING} with no {@code studio_run_step} row has nothing the per-step ceilings
 * can see and nothing that will ever report back: it holds its actor's concurrency slot and its
 * credit hold until the 4-hour run ceiling. It is failed with the code {@value #REASON_CODE}, cause
 * {@code TIMEOUT}, its hold released, and {@code evt.studio.run.failed} published.
 */
@Service
public class StalledRunService {

  /** The code a client reads in {@code error_message} to tell this ending from any other. */
  public static final String REASON_CODE = "RUN_DISPATCH_TIMEOUT";

  private static final Logger logger = LoggerFactory.getLogger(StalledRunService.class);

  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  private final RunSettlementService settlementService;
  private final RunAuditService runAuditService;
  private final OutboxService outboxService;
  private final RunMeasureRegistry measures;

  public StalledRunService(
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      RunSettlementService settlementService,
      RunAuditService runAuditService,
      OutboxService outboxService,
      MeterRegistry meterRegistry) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.settlementService = Objects.requireNonNull(settlementService, "settlement");
    this.runAuditService = Objects.requireNonNull(runAuditService, "RunAuditService required");
    this.outboxService = Objects.requireNonNull(outboxService, "OutboxService required");
    this.measures = new RunMeasureRegistry(meterRegistry);
  }

  /**
   * Fails the runs that started before {@code startedBefore} and still have no step.
   *
   * <p>A run a step called (it has a parent) is left to its parent's step ceiling. Rows are locked
   * with {@code SKIP LOCKED}, so two instances never close the same run.
   *
   * @param startedBefore the cutoff, from the caller's clock
   * @param limit the most runs closed in this call
   * @return how many runs were failed
   */
  @Transactional
  public int failUndispatched(Instant startedBefore, int limit) {
    List<Stalled> stalled =
        jdbcTemplate.query(
            "SELECT r.id, r.started_at, r.hold_id, r.actor_id, r.studio_key, r.blueprint_version,"
                + " r.correlation_id FROM studio_run r"
                + " WHERE r.finished_at IS NULL AND r.status = ? AND r.parent_run_id IS NULL"
                + " AND r.started_at < ?"
                + " AND NOT EXISTS (SELECT 1 FROM studio_run_step s WHERE s.run_id = r.id)"
                + " ORDER BY r.started_at LIMIT ? FOR UPDATE OF r SKIP LOCKED",
            (rs, rowNum) ->
                new Stalled(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getString("hold_id"),
                    rs.getString("actor_id"),
                    rs.getString("studio_key"),
                    rs.getString("blueprint_version"),
                    rs.getString("correlation_id")),
            RunStatus.RUNNING.name(),
            java.sql.Timestamp.from(startedBefore),
            limit);
    int failed = 0;
    for (Stalled run : stalled) {
      if (fail(run)) {
        failed++;
      }
    }
    return failed;
  }

  private boolean fail(Stalled run) {
    int updated =
        jdbcTemplate.update(
            "UPDATE studio_run SET status = ?, error_message = ?, failure_cause = ?,"
                + " finished_at = now() WHERE id = ? AND finished_at IS NULL",
            RunStatus.FAILED.name(),
            REASON_CODE + ": no step was dispatched for this run",
            FailureCause.TIMEOUT.name(),
            run.id());
    if (updated == 0) {
      return false;
    }
    logger.warn("Run {} dispatched no step — failing it and releasing its hold", run.id());
    settlementService.releaseAbandoned(run.holdId(), "swept: no step was dispatched");
    measures.failed(
        (Instant.now().toEpochMilli() - run.startedAt().toEpochMilli()) / 1000.0,
        FailureCause.TIMEOUT);
    runAuditService.recordFinished(
        run.id(), run.actorId(), run.studioKey(), false, FailureCause.TIMEOUT);
    outboxService.appendRunFailed(
        run.id(), run.actorId(), run.studioKey(), run.blueprintVersion(), run.correlationId());
    return true;
  }

  private record Stalled(
      UUID id,
      Instant startedAt,
      String holdId,
      String actorId,
      String studioKey,
      String blueprintVersion,
      String correlationId) {}
}
