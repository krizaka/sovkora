package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.Trigger;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import com.krizaka.sovkora.automationservice.domain.port.TriggerRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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
 * Triggers on {@code automation_trigger}. An owner-scoped statement joins the automation and
 * carries the owner; a trigger of someone else is never selected, updated or deleted.
 */
@Component
class JdbcTriggerRepositoryAdapter implements TriggerRepository {

  private static final String COLUMNS =
      "t.id, t.automation_id, t.kind::text AS kind, t.enabled, t.secret_hint, t.spec::text AS spec,"
          + " t.catch_up::text AS catch_up, t.next_fire_at, t.last_fired_at, t.created_at";
  private static final String OWNED =
      " FROM automation_trigger t JOIN automation a ON a.id = t.automation_id"
          + " WHERE a.user_id = ? AND a.runs_where = 'CLOUD' AND t.automation_id = ?";
  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcTriggerRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  @Override
  public int count(UUID automationId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM automation_trigger WHERE automation_id = ?",
            Integer.class,
            automationId);
    return n == null ? 0 : n;
  }

  @Override
  public Trigger createWebhook(UUID id, UUID automationId, String secretSealed, String secretHint) {
    return jdbc.queryForObject(
        "INSERT INTO automation_trigger AS t (id, automation_id, kind, secret_sealed, secret_hint)"
            + " VALUES (?, ?, 'WEBHOOK', ?, ?) RETURNING "
            + COLUMNS,
        (rs, rowNum) -> trigger(rs),
        id,
        automationId,
        secretSealed,
        secretHint);
  }

  @Override
  public Trigger createSchedule(
      UUID id, UUID automationId, ScheduleSpec schedule, CatchUp catchUp, Instant nextFireAt) {
    return jdbc.queryForObject(
        "INSERT INTO automation_trigger AS t (id, automation_id, kind, spec, catch_up, next_fire_at)"
            + " VALUES (?, ?, 'SCHEDULE', ?::jsonb, ?::automation_catch_up, ?) RETURNING "
            + COLUMNS,
        (rs, rowNum) -> trigger(rs),
        id,
        automationId,
        json.writeValueAsString(
            Map.of(
                "time", schedule.time().toString(),
                "zone", schedule.zone().getId(),
                "days", schedule.dayNames())),
        catchUp.name(),
        Timestamp.from(nextFireAt));
  }

  @Override
  public List<Trigger> list(String ownerId, UUID automationId) {
    return jdbc.query(
        "SELECT " + COLUMNS + OWNED + " ORDER BY t.created_at, t.id",
        (rs, rowNum) -> trigger(rs),
        ownerId,
        automationId);
  }

  @Override
  public Optional<Trigger> find(String ownerId, UUID automationId, UUID triggerId) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + OWNED + " AND t.id = ?",
            (rs, rowNum) -> trigger(rs),
            ownerId,
            automationId,
            triggerId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Trigger> setEnabled(
      String ownerId, UUID automationId, UUID triggerId, boolean enabled, Instant nextFireAt) {
    return jdbc
        .query(
            "UPDATE automation_trigger AS t SET enabled = ?,"
                + " next_fire_at = CASE WHEN t.kind = 'SCHEDULE' THEN ?::timestamptz ELSE NULL END"
                + " FROM automation a WHERE a.id = t.automation_id AND a.user_id = ?"
                + " AND t.automation_id = ? AND t.id = ? RETURNING "
                + COLUMNS,
            (rs, rowNum) -> trigger(rs),
            enabled,
            nextFireAt == null ? null : Timestamp.from(nextFireAt),
            ownerId,
            automationId,
            triggerId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean delete(String ownerId, UUID automationId, UUID triggerId) {
    return jdbc.update(
            "DELETE FROM automation_trigger t USING automation a WHERE a.id = t.automation_id"
                + " AND a.user_id = ? AND t.automation_id = ? AND t.id = ?",
            ownerId,
            automationId,
            triggerId)
        > 0;
  }

  @Override
  public Optional<Trigger> rotateSecret(
      String ownerId,
      UUID automationId,
      UUID triggerId,
      String secretSealed,
      String secretHint,
      Duration grace) {
    return jdbc
        .query(
            "UPDATE automation_trigger AS t SET previous_secret_sealed = t.secret_sealed,"
                + " previous_secret_until = now() + make_interval(secs => ?),"
                + " secret_sealed = ?, secret_hint = ? FROM automation a"
                + " WHERE a.id = t.automation_id AND a.user_id = ? AND t.automation_id = ?"
                + " AND t.id = ? AND t.kind = 'WEBHOOK' RETURNING "
                + COLUMNS,
            (rs, rowNum) -> trigger(rs),
            grace.toSeconds(),
            secretSealed,
            secretHint,
            ownerId,
            automationId,
            triggerId)
        .stream()
        .findFirst();
  }

  private Trigger trigger(ResultSet rs) throws SQLException {
    OffsetDateTime next = rs.getObject("next_fire_at", OffsetDateTime.class);
    OffsetDateTime last = rs.getObject("last_fired_at", OffsetDateTime.class);
    String spec = rs.getString("spec");
    TriggerKind kind = TriggerKind.valueOf(rs.getString("kind"));
    return new Trigger(
        rs.getObject("id", UUID.class),
        rs.getObject("automation_id", UUID.class),
        kind,
        rs.getBoolean("enabled"),
        rs.getString("secret_hint"),
        kind == TriggerKind.SCHEDULE ? schedule(spec) : null,
        CatchUp.valueOf(rs.getString("catch_up")),
        next == null ? null : next.toInstant(),
        last == null ? null : last.toInstant(),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  @SuppressWarnings("unchecked")
  private ScheduleSpec schedule(String spec) {
    Map<String, Object> map = json.readValue(spec, OBJECT);
    return ScheduleSpec.parse(
        (String) map.get("time"), (String) map.get("zone"), (List<String>) map.get("days"));
  }
}
