package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.DueSchedule;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.port.ScheduleStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The slots that have come (locked with SKIP LOCKED, so two instances never fire one twice). */
@Component
class JdbcScheduleStoreAdapter implements ScheduleStore {

  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcScheduleStoreAdapter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  @Override
  public List<DueSchedule> lockDueSchedules(Instant now, int limit) {
    return jdbc.query(
        "SELECT t.id, t.automation_id, t.spec::text AS spec, t.catch_up::text AS catch_up,"
            + " t.next_fire_at FROM automation_trigger t JOIN automation a ON a.id = t.automation_id"
            + " WHERE t.kind = 'SCHEDULE' AND t.enabled AND a.enabled AND t.next_fire_at <= ?"
            + " ORDER BY t.next_fire_at LIMIT ? FOR UPDATE OF t SKIP LOCKED",
        (rs, rowNum) ->
            new DueSchedule(
                rs.getObject("id", UUID.class),
                rs.getObject("automation_id", UUID.class),
                schedule(rs.getString("spec")),
                CatchUp.valueOf(rs.getString("catch_up")),
                rs.getObject("next_fire_at", OffsetDateTime.class).toInstant()),
        Timestamp.from(now),
        limit);
  }

  @Override
  public void advanceSchedule(UUID triggerId, Instant nextFireAt, Instant firedAt) {
    jdbc.update(
        "UPDATE automation_trigger SET next_fire_at = ?, last_fired_at = COALESCE(?::timestamptz, last_fired_at)"
            + " WHERE id = ?",
        Timestamp.from(nextFireAt),
        firedAt == null ? null : Timestamp.from(firedAt),
        triggerId);
  }

  @Override
  public void recordRun(UUID runId, UUID automationId, UUID triggerId, RunStatus status) {
    jdbc.update(
        "INSERT INTO automation_run (id, automation_id, trigger_id, trigger_kind, status,"
            + " finished_at) VALUES (?, ?, ?, 'SCHEDULE', ?::automation_run_status,"
            + " CASE WHEN ? THEN now() END)",
        runId,
        automationId,
        triggerId,
        status.name(),
        status.isFinal());
  }

  @SuppressWarnings("unchecked")
  private ScheduleSpec schedule(String spec) {
    Map<String, Object> map = json.readValue(spec, OBJECT);
    return ScheduleSpec.parse(
        (String) map.get("time"), (String) map.get("zone"), (List<String>) map.get("days"));
  }
}
