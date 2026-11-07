package com.krizaka.sovkora.engine.persistence.api.domain.model;

import java.util.Objects;

/**
 * Immutable request to append a message to the transactional outbox (AGENTS.md §6): the row is
 * committed with the caller's transaction and published to the broker asynchronously by the relay.
 *
 * @param aggregateType the aggregate the message belongs to (e.g. {@code job})
 * @param aggregateId the aggregate identifier (e.g. the job id)
 * @param exchange the target exchange ({@code orazaka.jobs} / {@code orazaka.events})
 * @param routingKey the routing key ({@code job.{capability}.{action}} / {@code evt.*})
 * @param payload the message payload, serialized to JSON by the store
 */
public record OutboxMessage(
    String aggregateType, String aggregateId, String exchange, String routingKey, Object payload) {

  public OutboxMessage {
    requireNonBlank(aggregateType, "aggregateType");
    requireNonBlank(aggregateId, "aggregateId");
    requireNonBlank(exchange, "exchange");
    requireNonBlank(routingKey, "routingKey");
    Objects.requireNonNull(payload, "payload cannot be null");
  }

  private static void requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " cannot be blank");
    }
  }
}
