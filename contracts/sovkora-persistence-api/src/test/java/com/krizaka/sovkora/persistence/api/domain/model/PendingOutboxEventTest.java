package com.krizaka.sovkora.engine.persistence.api.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingOutboxEventTest {

  @Test
  @DisplayName("Null payload defaults to an empty immutable map")
  void nullPayloadDefaultsToEmptyMap() {
    var event =
        new PendingOutboxEvent(
            UUID.randomUUID(), "orazaka.jobs", "job.text.process", UUID.randomUUID(), null, 0);

    assertThat(event.payload()).isEmpty();
  }

  @Test
  @DisplayName("Negative attempts are rejected")
  void negativeAttemptsRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new PendingOutboxEvent(
                    UUID.randomUUID(), "ex", "key", UUID.randomUUID(), Map.of(), -1));
  }

  @Test
  @DisplayName("Null identifiers are rejected")
  void nullIdentifiersRejected() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new PendingOutboxEvent(null, "ex", "key", UUID.randomUUID(), Map.of(), 0));
    assertThatNullPointerException()
        .isThrownBy(
            () -> new PendingOutboxEvent(UUID.randomUUID(), "ex", "key", null, Map.of(), 0));
  }
}
