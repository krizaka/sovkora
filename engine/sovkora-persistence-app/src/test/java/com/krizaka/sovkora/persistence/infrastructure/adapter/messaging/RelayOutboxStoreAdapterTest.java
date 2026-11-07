package com.krizaka.sovkora.persistence.infrastructure.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.messaging.outbox.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.model.PendingOutboxEvent;
import com.krizaka.sovkora.persistence.domain.ports.inbound.OutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RelayOutboxStoreAdapterTest {

  private final OutboxStore outboxStore = mock(OutboxStore.class);
  private final RelayOutboxStoreAdapter adapter =
      new RelayOutboxStoreAdapter(outboxStore, JsonMapper.builder().build());

  @Test
  void aPendingRowLeavesWithItsRoutingItsMessageIdAndAJsonBody() {
    UUID id = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    when(outboxStore.lockPendingBatch(10))
        .thenReturn(
            List.of(
                new PendingOutboxEvent(
                    id,
                    "platform.events",
                    "evt.user.registered",
                    messageId,
                    Map.of("email", "a@b.c"),
                    2)));

    OutboxMessage relayed = adapter.lockPendingBatch(10).getFirst();

    assertThat(relayed.id()).isEqualTo(id);
    assertThat(relayed.exchange()).isEqualTo("platform.events");
    assertThat(relayed.routingKey()).isEqualTo("evt.user.registered");
    assertThat(relayed.messageId()).isEqualTo(messageId.toString());
    assertThat(relayed.attempts()).isEqualTo(2);
    assertThat(new String(relayed.body(), StandardCharsets.UTF_8))
        .isEqualTo("{\"email\":\"a@b.c\"}");
  }

  @Test
  void bookkeepingIsDelegatedToTheContextsStore() {
    UUID id = UUID.randomUUID();
    Instant cutoff = Instant.now();
    when(outboxStore.purgePublishedBefore(cutoff)).thenReturn(4L);

    adapter.markPublished(id);
    adapter.recordFailure(id, 3);

    verify(outboxStore).markPublished(id);
    verify(outboxStore).recordFailure(id, 3);
    assertThat(adapter.purgePublishedBefore(cutoff)).isEqualTo(4);
  }
}
