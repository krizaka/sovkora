package com.krizaka.sovkora.engine.persistence.api.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

  @Test
  @DisplayName("Valid message is constructed")
  void validMessage() {
    var message =
        new OutboxMessage("job", "job-1", "orazaka.jobs", "job.text.process", Map.of("k", "v"));

    assertThat(message.aggregateType()).isEqualTo("job");
    assertThat(message.routingKey()).isEqualTo("job.text.process");
  }

  @Test
  @DisplayName("Blank fields are rejected by the compact constructor")
  void blankFieldsRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxMessage(" ", "job-1", "orazaka.jobs", "k", Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxMessage("job", "", "orazaka.jobs", "k", Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxMessage("job", "job-1", " ", "k", Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxMessage("job", "job-1", "orazaka.jobs", "", Map.of()));
  }

  @Test
  @DisplayName("Null payload is rejected")
  void nullPayloadRejected() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OutboxMessage("job", "job-1", "orazaka.jobs", "k", null));
  }
}
