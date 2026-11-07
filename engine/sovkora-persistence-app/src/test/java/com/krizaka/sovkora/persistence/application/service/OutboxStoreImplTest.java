package com.krizaka.sovkora.persistence.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.persistence.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.model.PendingOutboxEvent;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.OutboxEventEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxStoreImplTest {

  @Mock private OutboxEventRepository repository;

  private OutboxStoreImpl store;

  @BeforeEach
  void setUp() {
    store = new OutboxStoreImpl(repository, new ObjectMapper());
  }

  @Test
  @DisplayName("append serializes the payload and saves an unpublished row")
  void appendSavesUnpublishedRow() {
    record SamplePayload(String jobId, int size) {}

    store.append(
        new OutboxMessage(
            "job", "job-1", "orazaka.jobs", "job.text.process", new SamplePayload("job-1", 3)));

    ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
    verify(repository).save(captor.capture());
    OutboxEventEntity saved = captor.getValue();
    assertThat(saved.getExchange()).isEqualTo("orazaka.jobs");
    assertThat(saved.getRoutingKey()).isEqualTo("job.text.process");
    assertThat(saved.getAggregateId()).isEqualTo("job-1");
    assertThat(saved.getPayload()).containsEntry("jobId", "job-1").containsEntry("size", 3);
    assertThat(saved.getPublishedAt()).isNull();
    assertThat(saved.getMessageId()).isNotNull();
  }

  @Test
  @DisplayName("lockPendingBatch maps locked rows to pending events")
  void lockPendingBatchMapsRows() {
    var entity =
        new OutboxEventEntity(
            "job",
            "job-1",
            "orazaka.jobs",
            "job.text.process",
            Map.of("orazaka.core.chat.completion", "v"),
            Instant.now());
    when(repository.lockPendingBatch(10)).thenReturn(List.of(entity));

    List<PendingOutboxEvent> pending = store.lockPendingBatch(10);

    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().id()).isEqualTo(entity.getId());
    assertThat(pending.getFirst().messageId()).isEqualTo(entity.getMessageId());
    assertThat(pending.getFirst().payload()).containsEntry("orazaka.core.chat.completion", "v");
  }

  @Test
  @DisplayName("lockPendingBatch rejects a non-positive batch size")
  void lockPendingBatchRejectsNonPositiveSize() {
    assertThatIllegalArgumentException().isThrownBy(() -> store.lockPendingBatch(0));
  }

  @Test
  @DisplayName("markPublished stamps published_at")
  void markPublishedStampsRow() {
    var entity = new OutboxEventEntity("job", "job-1", "ex", "key", Map.of(), Instant.now());
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

    store.markPublished(entity.getId());

    verify(repository).save(entity);
    assertThat(entity.getPublishedAt()).isNotNull();
  }

  @Test
  @DisplayName("recordFailure increments attempts with exponential backoff")
  void recordFailureBacksOffExponentially() {
    var entity = new OutboxEventEntity("job", "job-1", "ex", "key", Map.of(), Instant.now());
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

    Instant before = Instant.now();
    store.recordFailure(entity.getId(), 2);

    assertThat(entity.getAttempts()).isEqualTo(3);
    // 2^3 = 8s backoff
    assertThat(entity.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(8));
    assertThat(entity.getNextAttemptAt()).isBeforeOrEqualTo(before.plusSeconds(70));
  }

  @Test
  @DisplayName("recordFailure caps the backoff at the maximum")
  void recordFailureCapsBackoff() {
    var entity = new OutboxEventEntity("job", "job-1", "ex", "key", Map.of(), Instant.now());
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

    Instant before = Instant.now();
    store.recordFailure(entity.getId(), 40);

    assertThat(entity.getNextAttemptAt()).isBeforeOrEqualTo(before.plusSeconds(61));
  }

  @Test
  @DisplayName("markPublished on an unknown event fails fast")
  void markPublishedUnknownEventFails() {
    UUID unknown = UUID.randomUUID();
    when(repository.findById(unknown)).thenReturn(Optional.empty());

    assertThatIllegalStateException().isThrownBy(() -> store.markPublished(unknown));
  }

  @Test
  @DisplayName("purgePublishedBefore delegates to the repository")
  void purgeDelegates() {
    Instant cutoff = Instant.now();
    when(repository.deleteByPublishedAtBefore(cutoff)).thenReturn(4L);

    assertThat(store.purgePublishedBefore(cutoff)).isEqualTo(4);
  }

  @Test
  @DisplayName("Null collaborators are rejected")
  void nullCollaboratorsRejected() {
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> new OutboxStoreImpl(null, new ObjectMapper()));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> new OutboxStoreImpl(repository, null));
  }

  @Test
  @DisplayName("append(null) fails and saves nothing")
  void appendNullFails() {
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> store.append(null));
    verify(repository, org.mockito.Mockito.never()).save(any());
  }
}
