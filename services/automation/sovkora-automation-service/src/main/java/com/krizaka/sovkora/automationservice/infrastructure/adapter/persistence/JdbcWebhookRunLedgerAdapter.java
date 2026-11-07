package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.port.WebhookRunLedger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The webhook ledger on {@code webhook_delivery}. The claim is one statement (data-modifying CTEs),
 * so the replay guard and the run row cannot come apart and no transaction is held.
 */
@Component
class JdbcWebhookRunLedgerAdapter implements WebhookRunLedger {

  private final JdbcTemplate jdbc;

  JdbcWebhookRunLedgerAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
  }

  @Override
  public Optional<UUID> existingWebhookRun(UUID triggerId, String webhookId) {
    return jdbc
        .query(
            "SELECT run_id FROM webhook_delivery WHERE trigger_id = ? AND webhook_id = ?",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            triggerId,
            webhookId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean recordWebhookRun(
      UUID runId,
      UUID automationId,
      UUID triggerId,
      String webhookId,
      String payload,
      int payloadBytes) {
    Integer claimed =
        jdbc.queryForObject(
            "WITH claim AS ("
                + " INSERT INTO webhook_delivery (trigger_id, webhook_id, run_id) VALUES (?, ?, ?)"
                + " ON CONFLICT DO NOTHING RETURNING 1),"
                + " new_run AS ("
                + " INSERT INTO automation_run (id, automation_id, trigger_id, trigger_kind, status,"
                + " payload, payload_bytes, webhook_id)"
                + " SELECT ?, ?, ?, 'WEBHOOK'::automation_trigger_kind,"
                + " 'QUEUED'::automation_run_status, ?::jsonb, ?, ?"
                + " WHERE EXISTS (SELECT 1 FROM claim) RETURNING 1)"
                + " SELECT (SELECT count(*) FROM new_run)::int",
            Integer.class,
            triggerId,
            webhookId,
            runId,
            runId,
            automationId,
            triggerId,
            payload,
            payloadBytes,
            webhookId);
    return claimed != null && claimed == 1;
  }

  @Override
  public int purgeDeliveries(Instant before) {
    return jdbc.update(
        "DELETE FROM webhook_delivery WHERE received_at < ?", Timestamp.from(before));
  }
}
