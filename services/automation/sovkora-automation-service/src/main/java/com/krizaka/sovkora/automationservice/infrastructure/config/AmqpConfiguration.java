package com.krizaka.sovkora.automationservice.infrastructure.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the external services worker (AGENTS.md §6): binds its own queues to the
 * shared topic exchanges {@code orazaka.jobs} / {@code orazaka.events}, each queue dead-lettering
 * to its {@code <queue>.dlq} through {@code orazaka.dlx}. Exchange declarations are idempotent
 * duplicates of the platform topology (same type/durability) — required so the worker can start
 * before any producer app has declared them.
 */
@Configuration
public class AmqpConfiguration {

  @Bean
  public TopicExchange jobsExchange() {
    return new TopicExchange(AmqpConstants.JOBS_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(AmqpConstants.EVENTS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(AmqpConstants.DLX_EXCHANGE, true, false);
  }

  // ── Automation jobs — job.automation.* ────────────────────────────────────

  @Bean
  public Queue automationJobsQueue() {
    return new Queue(
        AmqpConstants.AUTOMATION_QUEUE,
        true,
        false,
        false,
        dlqArguments(AmqpConstants.AUTOMATION_QUEUE));
  }

  @Bean
  public Binding automationJobsBinding(Queue automationJobsQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(automationJobsQueue)
        .to(jobsExchange)
        .with(AmqpConstants.AUTOMATION_BINDING);
  }

  @Bean
  public Queue automationJobsDlq() {
    return new Queue(AmqpConstants.AUTOMATION_DLQ, true, false, false);
  }

  @Bean
  public Binding automationJobsDlqBinding(
      Queue automationJobsDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(automationJobsDlq)
        .to(deadLetterExchange)
        .with(AmqpConstants.AUTOMATION_QUEUE);
  }

  // ── Studio run outcomes — evt.studio.run.{succeeded,failed} ───────────────

  @Bean
  public Queue studioRunsQueue() {
    return new Queue(
        AmqpConstants.STUDIO_RUNS_QUEUE,
        true,
        false,
        false,
        dlqArguments(AmqpConstants.STUDIO_RUNS_QUEUE));
  }

  @Bean
  public Binding studioRunSucceededBinding(Queue studioRunsQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(studioRunsQueue)
        .to(eventsExchange)
        .with(AmqpConstants.STUDIO_RUN_SUCCEEDED);
  }

  @Bean
  public Binding studioRunFailedBinding(Queue studioRunsQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(studioRunsQueue)
        .to(eventsExchange)
        .with(AmqpConstants.STUDIO_RUN_FAILED);
  }

  @Bean
  public Queue studioRunsDlq() {
    return new Queue(AmqpConstants.STUDIO_RUNS_DLQ, true, false, false);
  }

  @Bean
  public Binding studioRunsDlqBinding(Queue studioRunsDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(studioRunsDlq)
        .to(deadLetterExchange)
        .with(AmqpConstants.STUDIO_RUNS_QUEUE);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  private static Map<String, Object> dlqArguments(String queueName) {
    return Map.of(
        "x-dead-letter-exchange",
        AmqpConstants.DLX_EXCHANGE,
        "x-dead-letter-routing-key",
        queueName);
  }
}
