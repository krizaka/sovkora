package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.AutomationRun;
import com.krizaka.sovkora.automationservice.domain.model.RunDetail;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import com.krizaka.sovkora.automationservice.domain.port.RunHistoryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/** The history of an owner's runs: newest first, always joined to the owner's automation. */
@Component
class JdbcRunHistoryAdapter implements RunHistoryRepository {

  private static final String RUN_COLUMNS =
      "r.id, r.automation_id, a.name AS automation_name, r.trigger_id, r.trigger_kind::text AS trigger_kind,"
          + " r.status::text AS status, r.studio_run_id, r.error_code, r.error_message, r.webhook_id,"
          + " r.payload_bytes, r.started_at, r.finished_at";
  private static final String RUN_FROM =
      " FROM automation_run r JOIN automation a ON a.id = r.automation_id WHERE a.user_id = ?";
  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcRunHistoryAdapter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  @Override
  public List<AutomationRun> list(String ownerId, UUID automationId, int limit) {
    return jdbc.query(
        "SELECT "
            + RUN_COLUMNS
            + RUN_FROM
            + (automationId == null ? "" : " AND r.automation_id = ?")
            + " ORDER BY r.started_at DESC, r.id LIMIT ?",
        (rs, rowNum) -> run(rs),
        automationId == null
            ? new Object[] {ownerId, limit}
            : new Object[] {ownerId, automationId, limit});
  }

  @Override
  public Optional<RunDetail> find(String ownerId, UUID runId) {
    return jdbc
        .query(
            "SELECT " + RUN_COLUMNS + ", r.payload::text AS payload" + RUN_FROM + " AND r.id = ?",
            (rs, rowNum) -> {
              String payload = rs.getString("payload");
              return new RunDetail(
                  run(rs), payload == null ? null : json.readValue(payload, OBJECT));
            },
            ownerId,
            runId)
        .stream()
        .findFirst();
  }

  private static AutomationRun run(ResultSet rs) throws SQLException {
    String kind = rs.getString("trigger_kind");
    int bytes = rs.getInt("payload_bytes");
    boolean noBytes = rs.wasNull();
    OffsetDateTime finished = rs.getObject("finished_at", OffsetDateTime.class);
    return new AutomationRun(
        rs.getObject("id", UUID.class),
        rs.getObject("automation_id", UUID.class),
        rs.getString("automation_name"),
        rs.getObject("trigger_id", UUID.class),
        kind == null ? null : TriggerKind.valueOf(kind),
        RunStatus.valueOf(rs.getString("status")),
        rs.getObject("studio_run_id", UUID.class),
        rs.getString("error_code"),
        rs.getString("error_message"),
        rs.getString("webhook_id"),
        noBytes ? null : bytes,
        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
        finished == null ? null : finished.toInstant());
  }
}
