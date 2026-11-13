package com.krizaka.sovkora.studio.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends again the command of a step whose outcome has not come back (krizaka-internal#2132, ADR
 * SOV-105 §2).
 *
 * <p>The broker of the pilot is one small machine: if it is lost, the messages in flight go with
 * it. The state of a paid file is not in them — the run, its step and the command it was dispatched
 * with are rows. A step still {@code RUNNING} past its lane's ceiling, with re-issues left, gets
 * its command appended to the outbox again <b>with the same job id</b>: the executor dedups on it,
 * and an outcome that arrives twice is applied once (the guarded {@code UPDATE} of {@code
 * RunSagaService.applyOutcome}).
 *
 * <p>The count is on the step row and the maximum is a row of {@code studio_runtime_config}, so a
 * restart cannot loop it. Rows are claimed with {@code SKIP LOCKED}: two instances never send the
 * same step twice.
 */
@Service
public class StepReissueService {

  private static final Logger logger = LoggerFactory.getLogger(StepReissueService.class);

  /** The most steps one sweep sends again; the next sweep takes the rest. @fixed: a page size. */
  static final int BATCH_SIZE = 100;

  private final JdbcTemplate jdbcTemplate;
  private final OutboxService outboxService;
  private final MeterRegistry meterRegistry;

  public StepReissueService(
      JdbcTemplate jdbcTemplate, OutboxService outboxService, MeterRegistry meterRegistry) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.outboxService = Objects.requireNonNull(outboxService, "OutboxService required");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry required");
  }

  /**
   * Re-issues the silent steps that have re-issues left.
   *
   * <p>A step is silent when its ceiling has passed since the later of its dispatch and its last
   * re-issue. A step whose last copy has not reached the broker yet is left alone: the relay is
   * still trying, and a second copy behind it would only pile up.
   *
   * @param interactiveSeconds the ceiling of the INTERACTIVE lane
   * @param batchSeconds the ceiling of the BATCH lane
   * @param maxReissues the most times one step is sent again; 0 sends nothing
   * @return how many steps were sent again
   */
  @Transactional
  public int reissueSilentSteps(int interactiveSeconds, int batchSeconds, int maxReissues) {
    if (maxReissues <= 0) {
      return 0;
    }
    List<Silent> silent =
        jdbcTemplate.query(
            "SELECT s.run_id, s.step_id, s.ordinal, s.job_id,"
                + " COALESCE(s.latency_class, 'BATCH') AS lane"
                + " FROM studio_run_step s JOIN studio_run r ON r.id = s.run_id"
                + " WHERE r.finished_at IS NULL AND r.status = 'RUNNING' AND s.status = 'RUNNING'"
                + " AND s.job_id IS NOT NULL AND s.reissues < ?"
                + " AND COALESCE(s.reissued_at, s.started_at) < now() - make_interval(secs => CASE"
                + "   WHEN COALESCE(s.latency_class, 'BATCH') = 'INTERACTIVE' THEN ? ELSE ? END)"
                + " AND (SELECT o.published_at IS NOT NULL FROM studio_outbox o"
                + "      WHERE o.message_id = s.job_id ORDER BY o.created_at DESC LIMIT 1)"
                + " ORDER BY s.started_at LIMIT ? FOR UPDATE OF s SKIP LOCKED",
            (rs, rowNum) ->
                new Silent(
                    rs.getObject("run_id", java.util.UUID.class),
                    rs.getString("step_id"),
                    rs.getInt("ordinal"),
                    rs.getString("job_id"),
                    rs.getString("lane")),
            maxReissues,
            interactiveSeconds,
            batchSeconds,
            BATCH_SIZE);
    int sent = 0;
    for (Silent step : silent) {
      if (!outboxService.repeatCommand(step.jobId())) {
        continue;
      }
      jdbcTemplate.update(
          "UPDATE studio_run_step SET reissues = reissues + 1, reissued_at = now()"
              + " WHERE run_id = ? AND step_id = ? AND ordinal = ?",
          step.runId(),
          step.stepId(),
          step.ordinal());
      meterRegistry.counter("sovkora.studio.step.reissued", "lane", step.lane()).increment();
      logger.warn(
          "Step {}[{}] of run {} ({} lane) is silent past its ceiling — command of job {} sent"
              + " again",
          step.stepId(),
          step.ordinal(),
          step.runId(),
          step.lane(),
          step.jobId());
      sent++;
    }
    return sent;
  }

  private record Silent(
      java.util.UUID runId, String stepId, int ordinal, String jobId, String lane) {}
}
