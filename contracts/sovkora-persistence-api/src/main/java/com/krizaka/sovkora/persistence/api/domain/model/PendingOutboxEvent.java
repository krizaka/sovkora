package com.krizaka.sovkora.persistence.api.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read view of an unpublished outbox row handed to the relay: everything needed to publish the
 * message (destination, idempotency key, JSON payload) plus the attempt counter driving the
 * exponential backoff.
 *
 * @param id the outbox row identifier
 * @param exchange the target exchange
 * @param routingKey the routing key
 * @param messageId the AMQP message id consumers deduplicate on (AGENTS.md §6)
 * @param payload the JSON payload as a map
 * @param attempts publish attempts so far
 */
public record PendingOutboxEvent(
    UUID id,
    String exchange,
    String routingKey,
    UUID messageId,
    Map<String, Object> payload,
    int attempts) {

  public PendingOutboxEvent {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(exchange, "exchange cannot be null");
    Objects.requireNonNull(routingKey, "routingKey cannot be null");
    Objects.requireNonNull(messageId, "messageId cannot be null");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts cannot be negative");
    }
  }
}
