package com.krizaka.sovkora.persistence.domain.ports.inbound;

import com.krizaka.sovkora.persistence.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.model.PendingOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional outbox (AGENTS.md §6, INTERFACES.md §9): producers append messages inside their
 * business transaction; the relay drains the table and publishes to RabbitMQ with the row's {@code
 * messageId}, giving at-least-once delivery with consumer-side dedup.
 */
public interface OutboxStore {

  /**
   * Appends a message to the outbox. Joins the caller's transaction when one is active, so the
   * business write and the message commit or roll back together.
   *
   * @param message the message to enqueue for publication
   */
  void append(OutboxMessage message);

  /**
   * Locks and returns the next batch of unpublished events whose backoff has elapsed ({@code FOR
   * UPDATE SKIP LOCKED}). Must be called within an active transaction — the row locks protect the
   * batch until the relay commits.
   *
   * @param batchSize maximum number of events to lock
   * @return the locked pending events, oldest first
   */
  List<PendingOutboxEvent> lockPendingBatch(int batchSize);

  /**
   * Marks an event as published. Must run in the same transaction that locked it.
   *
   * @param eventId the outbox row id
   */
  void markPublished(UUID eventId);

  /**
   * Records a failed publish attempt and schedules the next retry with exponential backoff. Must
   * run in the same transaction that locked the event.
   *
   * @param eventId the outbox row id
   * @param previousAttempts the attempt count before this failure
   */
  void recordFailure(UUID eventId, int previousAttempts);

  /**
   * Deletes published events older than the cutoff (housekeeping).
   *
   * @param cutoff events published before this instant are removed
   * @return the number of rows deleted
   */
  long purgePublishedBefore(Instant cutoff);
}
