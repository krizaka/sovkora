package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventEntityTest {

  @Test
  @DisplayName("A fresh row is unpublished, has generated ids, and is eligible immediately")
  void freshRowIsUnpublishedAndEligible() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var entity =
        new OutboxEventEntity(
            "job",
            "job-1",
            "orazaka.jobs",
            "job.text.process",
            Map.of("orazaka.core.chat.completion", "v"),
            now);

    assertThat(entity.getId()).isNotNull();
    assertThat(entity.getMessageId()).isNotNull();
    assertThat(entity.getMessageId()).isNotEqualTo(entity.getId());
    assertThat(entity.getPublishedAt()).isNull();
    assertThat(entity.getAttempts()).isZero();
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getNextAttemptAt()).isEqualTo(now);
    assertThat(entity.getAggregateType()).isEqualTo("job");
    assertThat(entity.getAggregateId()).isEqualTo("job-1");
    assertThat(entity.getExchange()).isEqualTo("orazaka.jobs");
    assertThat(entity.getRoutingKey()).isEqualTo("job.text.process");
    assertThat(entity.getPayload()).containsEntry("orazaka.core.chat.completion", "v");
  }

  @Test
  @DisplayName("Mutable lifecycle fields are settable (published_at, attempts, next_attempt_at)")
  void lifecycleFieldsAreMutable() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var entity = new OutboxEventEntity("job", "job-1", "ex", "key", Map.of(), now);

    entity.setPublishedAt(now.plusSeconds(1));
    entity.setAttempts(3);
    entity.setNextAttemptAt(now.plusSeconds(8));

    assertThat(entity.getPublishedAt()).isEqualTo(now.plusSeconds(1));
    assertThat(entity.getAttempts()).isEqualTo(3);
    assertThat(entity.getNextAttemptAt()).isEqualTo(now.plusSeconds(8));
  }
}
