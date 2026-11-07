package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.DispatchTarget;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.RunningStudioRun;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The life of a recorded run on {@code automation_run}: every statement is one short transaction.
 */
@Component
class JdbcRunRepositoryAdapter implements RunRepository {

  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcRunRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  @Override
  public int runsSince(UUID automationId, Instant since) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM automation_run WHERE automation_id = ? AND started_at >= ?",
            Integer.class,
            automationId,
            Timestamp.from(since));
    return n == null ? 0 : n;
  }

  @Override
  public Optional<DispatchTarget> dispatchTarget(UUID runId) {
    return jdbc
        .query(
            "SELECT r.id, a.user_id, a.recipe, a.inputs::text AS inputs, r.status::text AS status"
                + " FROM automation_run r JOIN automation a ON a.id = r.automation_id"
                + " WHERE r.id = ? AND a.enabled",
            (rs, rowNum) ->
                new DispatchTarget(
                    rs.getObject("id", UUID.class),
                    rs.getString("user_id"),
                    studioKey(rs.getString("recipe")),
                    json.readValue(rs.getString("inputs"), OBJECT),
                    RunStatus.valueOf(rs.getString("status"))),
            runId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean markRunning(UUID runId, UUID studioRunId) {
    return jdbc.update(
            "UPDATE automation_run SET status = 'RUNNING', studio_run_id = ?"
                + " WHERE id = ? AND status = 'QUEUED'",
            studioRunId,
            runId)
        > 0;
  }

  @Override
  public boolean markFailed(UUID runId, String code, String message) {
    return jdbc.update(
            "UPDATE automation_run SET status = 'FAILED', error_code = ?, error_message = ?,"
                + " finished_at = now() WHERE id = ? AND status IN ('QUEUED', 'RUNNING')",
            code,
            message,
            runId)
        > 0;
  }

  @Override
  public boolean finishByStudioRun(UUID studioRunId, RunStatus status, String errorCode) {
    return jdbc.update(
            "UPDATE automation_run SET status = ?::automation_run_status, error_code = ?,"
                + " finished_at = now() WHERE studio_run_id = ? AND status = 'RUNNING'",
            status.name(),
            errorCode,
            studioRunId)
        > 0;
  }

  @Override
  public List<UUID> staleQueued(Instant before, int limit) {
    return jdbc.query(
        "SELECT r.id FROM automation_run r WHERE r.status = 'QUEUED' AND r.started_at < ?"
            + " ORDER BY r.started_at LIMIT ?",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        Timestamp.from(before),
        limit);
  }

  @Override
  public List<RunningStudioRun> staleRunning(Instant before, int limit) {
    return jdbc.query(
        "SELECT r.id, r.studio_run_id, a.user_id FROM automation_run r"
            + " JOIN automation a ON a.id = r.automation_id"
            + " WHERE r.status = 'RUNNING' AND r.studio_run_id IS NOT NULL AND r.started_at < ?"
            + " ORDER BY r.started_at LIMIT ?",
        (rs, rowNum) ->
            new RunningStudioRun(
                rs.getObject("id", UUID.class),
                rs.getObject("studio_run_id", UUID.class),
                rs.getString("user_id")),
        Timestamp.from(before),
        limit);
  }

  private static String studioKey(String recipe) {
    return recipe.startsWith("studio:") ? recipe.substring("studio:".length()) : recipe;
  }
}
