package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.model.PendingOutboxEvent;
import com.krizaka.sovkora.persistence.domain.ports.inbound.OutboxStore;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.OutboxEventEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Package-private implementation of the transactional {@link OutboxStore}. */
@Service
class OutboxStoreImpl implements OutboxStore {

  /** Backoff cap so a poisoned event retries at most once per minute. */
  private static final long MAX_BACKOFF_SECONDS = 60;

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;

  OutboxStoreImpl(OutboxEventRepository repository, ObjectMapper objectMapper) {
    this.repository = Objects.requireNonNull(repository, "OutboxEventRepository cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
  }

  @Override
  @Transactional
  public void append(OutboxMessage message) {
    Objects.requireNonNull(message, "OutboxMessage cannot be null");
    Map<String, Object> payload = objectMapper.convertValue(message.payload(), MAP_TYPE);
    repository.save(
        new OutboxEventEntity(
            message.aggregateType(),
            message.aggregateId(),
            message.exchange(),
            message.routingKey(),
            payload,
            Instant.now()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public List<PendingOutboxEvent> lockPendingBatch(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    return repository.lockPendingBatch(batchSize).stream().map(OutboxStoreImpl::toPending).toList();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void markPublished(UUID eventId) {
    OutboxEventEntity entity = requireEvent(eventId);
    entity.setPublishedAt(Instant.now());
    repository.save(entity);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordFailure(UUID eventId, int previousAttempts) {
    OutboxEventEntity entity = requireEvent(eventId);
    int attempts = previousAttempts + 1;
    long backoffSeconds = Math.min(1L << Math.min(attempts, 30), MAX_BACKOFF_SECONDS);
    entity.setAttempts(attempts);
    entity.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
    repository.save(entity);
  }

  @Override
  @Transactional
  public long purgePublishedBefore(Instant cutoff) {
    Objects.requireNonNull(cutoff, "cutoff cannot be null");
    return repository.deleteByPublishedAtBefore(cutoff);
  }

  private OutboxEventEntity requireEvent(UUID eventId) {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    return repository
        .findById(eventId)
        .orElseThrow(() -> new IllegalStateException("Unknown outbox event: " + eventId));
  }

  private static PendingOutboxEvent toPending(OutboxEventEntity entity) {
    return new PendingOutboxEvent(
        entity.getId(),
        entity.getExchange(),
        entity.getRoutingKey(),
        entity.getMessageId(),
        entity.getPayload(),
        entity.getAttempts());
  }
}
