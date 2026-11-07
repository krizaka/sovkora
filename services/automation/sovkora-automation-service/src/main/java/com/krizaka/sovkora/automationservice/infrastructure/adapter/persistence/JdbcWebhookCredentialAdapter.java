package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.TriggerCredentials;
import com.krizaka.sovkora.automationservice.domain.port.WebhookCredentialStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What the webhook ingress reads about a trigger: its sealed secrets and whether it is active. */
@Component
class JdbcWebhookCredentialAdapter implements WebhookCredentialStore {

  private final JdbcTemplate jdbc;

  JdbcWebhookCredentialAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
  }

  @Override
  public Optional<TriggerCredentials> credentials(UUID triggerId) {
    return jdbc
        .query(
            "SELECT t.id, t.automation_id, (t.enabled AND a.enabled) AS active, t.secret_sealed,"
                + " t.previous_secret_sealed, t.previous_secret_until"
                + " FROM automation_trigger t JOIN automation a ON a.id = t.automation_id"
                + " WHERE t.id = ? AND t.kind = 'WEBHOOK' AND t.secret_sealed IS NOT NULL",
            (rs, rowNum) -> {
              OffsetDateTime until = rs.getObject("previous_secret_until", OffsetDateTime.class);
              return new TriggerCredentials(
                  rs.getObject("id", UUID.class),
                  rs.getObject("automation_id", UUID.class),
                  rs.getBoolean("active"),
                  rs.getString("secret_sealed"),
                  rs.getString("previous_secret_sealed"),
                  until == null ? null : until.toInstant());
            },
            triggerId)
        .stream()
        .findFirst();
  }

  @Override
  public int clearExpiredPreviousSecrets(Instant now) {
    return jdbc.update(
        "UPDATE automation_trigger SET previous_secret_sealed = NULL, previous_secret_until = NULL"
            + " WHERE previous_secret_sealed IS NOT NULL AND previous_secret_until < ?",
        Timestamp.from(now));
  }
}
