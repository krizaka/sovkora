package com.krizaka.sovkora.persistence.infrastructure.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Standard configuration properties record for native queue settings under {@code
 * orazaka.messaging.broker}.
 *
 * <p>{@code queue} is {@link DefaultValue}-annotated so that a host importing {@code
 * JobsTopologyConfig} from this shared library boots on the nested defaults when it declares no
 * {@code orazaka.messaging.broker} block — an omitted section is a valid configuration, not a
 * startup failure.
 */
@ConfigurationProperties(prefix = "orazaka.messaging.broker")
public record BrokerProperties(@DefaultValue QueueProperties queue) {

  public BrokerProperties {
    Objects.requireNonNull(queue, "Broker queue properties cannot be null");
  }

  /** Nested queue configuration properties. */
  public record QueueProperties(
      @DefaultValue("1000") Integer maxLength,
      @DefaultValue("rejectPublish") String overflowStrategy) {
    public QueueProperties {
      Objects.requireNonNull(maxLength, "max-length is required");
      Objects.requireNonNull(overflowStrategy, "overflow-strategy is required");
    }
  }
}
