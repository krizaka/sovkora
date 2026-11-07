package com.krizaka.sovkora.persistence.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class EventsTopologyConfigTest {

  private EventsTopologyConfig config;

  @BeforeEach
  void setUp() {
    config = new EventsTopologyConfig();
  }

  @Test
  @DisplayName("orazaka.events is a durable, non-auto-delete topic exchange")
  void eventsExchangeIsDurableTopic() {
    var exchange = config.eventsExchange();

    assertThat(exchange.getName()).isEqualTo(MessagingContract.EVENTS_EXCHANGE);
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }

  @Test
  @DisplayName("Job-relay queue binds job.# and dead-letters to orazaka.dlx")
  void jobRelayQueueTopology() {
    Queue queue = config.eventsJobRelayQueue();
    Binding binding = config.eventsJobRelayBinding(queue, config.eventsExchange());

    assertThat(queue.getName()).isEqualTo(MessagingContract.EVENTS_JOB_RELAY_QUEUE);
    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", MessagingContract.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", MessagingContract.EVENTS_JOB_RELAY_QUEUE);
    assertThat(binding.getExchange()).isEqualTo(MessagingContract.EVENTS_EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo(MessagingContract.EVENTS_JOB_RELAY_BINDING);
  }

  @Test
  @DisplayName("Job-relay DLQ binds to the DLX with the source queue name as key")
  void jobRelayDlqBinding() {
    Queue dlq = config.eventsJobRelayDlq();
    Binding dlqBinding =
        config.eventsJobRelayDlqBinding(
            dlq, new DirectExchange(MessagingContract.DLX_EXCHANGE, true, false));

    assertThat(dlq.getName()).isEqualTo(MessagingContract.EVENTS_JOB_RELAY_DLQ);
    assertThat(dlqBinding.getExchange()).isEqualTo(MessagingContract.DLX_EXCHANGE);
    assertThat(dlqBinding.getDestination()).isEqualTo(MessagingContract.EVENTS_JOB_RELAY_DLQ);
    assertThat(dlqBinding.getRoutingKey()).isEqualTo(MessagingContract.EVENTS_JOB_RELAY_QUEUE);
  }
}
