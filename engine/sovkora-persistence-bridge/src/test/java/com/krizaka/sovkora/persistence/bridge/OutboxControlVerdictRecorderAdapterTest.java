package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.krizaka.sovkora.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.sovkora.persistence.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.ports.inbound.OutboxStore;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxControlVerdictRecorderAdapterTest {

  @Mock private OutboxStore outboxStore;

  @Test
  @SuppressWarnings("unchecked")
  void aVerdictLeavesOneOutboxMessageWithTheFiveFields() {
    var recorder = new OutboxControlVerdictRecorderAdapter(outboxStore);
    var at = Instant.parse("2026-10-26T12:00:00Z");

    recorder.record(
        new ControlVerdictRecord("run-7", "STRUCTURAL_A: bad", "op-1", at, "ESCALATED"));

    var message = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxStore).append(message.capture());
    assertThat(message.getValue().aggregateId()).isEqualTo("run-7");
    assertThat(message.getValue().routingKey()).isEqualTo("evt.control.verdict");
    assertThat((Map<String, Object>) message.getValue().payload())
        .containsEntry("runId", "run-7")
        .containsEntry("driftReason", "STRUCTURAL_A: bad")
        .containsEntry("humanActorId", "op-1")
        .containsEntry("timestamp", "2026-10-26T12:00:00Z")
        .containsEntry("actionTaken", "ESCALATED");
  }
}
