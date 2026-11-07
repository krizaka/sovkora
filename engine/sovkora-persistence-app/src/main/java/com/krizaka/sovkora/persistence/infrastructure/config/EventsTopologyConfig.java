package com.krizaka.sovkora.persistence.infrastructure.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code orazaka.events} topic exchange topology (AGENTS.md §6): domain events use
 * {@code evt.{aggregate}.{type}} keys, job lifecycle events use {@code
 * job.{jobId}.progress|done|error}. This side declares only the job-relay queue feeding the SSE
 * streams; event consumers (integrations worker) declare and bind their own queues.
 */
@Configuration
public class EventsTopologyConfig {

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(MessagingContract.EVENTS_EXCHANGE, true, false);
  }

  @Bean
  public Queue eventsJobRelayQueue() {
    return new Queue(
        MessagingContract.EVENTS_JOB_RELAY_QUEUE,
        true,
        false,
        false,
        Map.of(
            "x-dead-letter-exchange", MessagingContract.DLX_EXCHANGE,
            "x-dead-letter-routing-key", MessagingContract.EVENTS_JOB_RELAY_QUEUE));
  }

  @Bean
  public Binding eventsJobRelayBinding(Queue eventsJobRelayQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(eventsJobRelayQueue)
        .to(eventsExchange)
        .with(MessagingContract.EVENTS_JOB_RELAY_BINDING);
  }

  @Bean
  public Queue eventsJobRelayDlq() {
    return new Queue(MessagingContract.EVENTS_JOB_RELAY_DLQ, true, false, false);
  }

  @Bean
  public Binding eventsJobRelayDlqBinding(
      Queue eventsJobRelayDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(eventsJobRelayDlq)
        .to(deadLetterExchange)
        .with(MessagingContract.EVENTS_JOB_RELAY_QUEUE);
  }
}
