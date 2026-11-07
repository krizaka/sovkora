package com.krizaka.sovkora.persistence.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code orazaka.jobs} topic exchange topology (AGENTS.md §6): per-capability work
 * queues bound with {@code job.{capability}.*} patterns, each dead-lettering to its own {@code
 * <queue>.dlq} through the shared {@code orazaka.dlx} exchange. Retry/backoff policy is
 * listener-side yaml wiring ({@code spring.rabbitmq.listener.simple.retry}).
 */
@Configuration
@EnableConfigurationProperties(BrokerProperties.class)
public class JobsTopologyConfig {

  private final BrokerProperties brokerProperties;

  public JobsTopologyConfig(BrokerProperties brokerProperties) {
    this.brokerProperties =
        Objects.requireNonNull(brokerProperties, "BrokerProperties cannot be null");
  }

  @Bean
  public TopicExchange jobsExchange() {
    return new TopicExchange(MessagingContract.JOBS_EXCHANGE, true, false);
  }

  /** Shared dead-letter exchange; every DLQ binds to it with its source queue name as key. */
  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(MessagingContract.DLX_EXCHANGE, true, false);
  }

  /**
   * The BATCH lane: work whose duration follows what the user asked to produce (ADR-067).
   *
   * <p>An image generation is p50 67 s on this machine. Sharing a queue with a 4.9 s analysis meant
   * two generations occupied both consumer slots and every analysis waited behind them.
   */
  @Bean
  public Queue jobsBatchQueue() {
    return new Queue(
        MessagingContract.JOBS_BATCH_QUEUE,
        true,
        false,
        false,
        workQueueArguments(MessagingContract.JOBS_BATCH_QUEUE));
  }

  @Bean
  public Binding jobsBatchBinding(Queue jobsBatchQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(jobsBatchQueue)
        .to(jobsExchange)
        .with(MessagingContract.JOBS_BATCH_BINDING);
  }

  @Bean
  public Queue jobsBatchDlq() {
    return new Queue(MessagingContract.JOBS_BATCH_DLQ, true, false, false);
  }

  @Bean
  public Binding jobsBatchDlqBinding(Queue jobsBatchDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(jobsBatchDlq)
        .to(deadLetterExchange)
        .with(MessagingContract.JOBS_BATCH_QUEUE);
  }

  @Bean
  public Queue jobsVideoQueue() {
    return new Queue(
        MessagingContract.JOBS_VIDEO_QUEUE,
        true,
        false,
        false,
        workQueueArguments(MessagingContract.JOBS_VIDEO_QUEUE));
  }

  @Bean
  public Binding jobsVideoBinding(Queue jobsVideoQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(jobsVideoQueue)
        .to(jobsExchange)
        .with(MessagingContract.JOBS_VIDEO_BINDING);
  }

  @Bean
  public Queue jobsVideoDlq() {
    return new Queue(MessagingContract.JOBS_VIDEO_DLQ, true, false, false);
  }

  @Bean
  public Binding jobsVideoDlqBinding(Queue jobsVideoDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(jobsVideoDlq)
        .to(deadLetterExchange)
        .with(MessagingContract.JOBS_VIDEO_QUEUE);
  }

  /**
   * The INTERACTIVE lane: work bounded by the model's own speed — a turn, one image to describe.
   *
   * <p>Two bindings on one queue, because a lane is a property of the WORK and not of the media
   * type: {@code job.text.*} and {@code job.media.analyze} are both things a person is waiting
   * through (ADR-067).
   */
  @Bean
  public Queue jobsInteractiveQueue() {
    return new Queue(
        MessagingContract.JOBS_INTERACTIVE_QUEUE,
        true,
        false,
        false,
        workQueueArguments(MessagingContract.JOBS_INTERACTIVE_QUEUE));
  }

  @Bean
  public Binding jobsInteractiveTextBinding(
      Queue jobsInteractiveQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(jobsInteractiveQueue)
        .to(jobsExchange)
        .with(MessagingContract.JOBS_INTERACTIVE_TEXT_BINDING);
  }

  @Bean
  public Binding jobsInteractiveMediaBinding(
      Queue jobsInteractiveQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(jobsInteractiveQueue)
        .to(jobsExchange)
        .with(MessagingContract.JOBS_INTERACTIVE_MEDIA_BINDING);
  }

  @Bean
  public Queue jobsInteractiveDlq() {
    return new Queue(MessagingContract.JOBS_INTERACTIVE_DLQ, true, false, false);
  }

  @Bean
  public Binding jobsInteractiveDlqBinding(
      Queue jobsInteractiveDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(jobsInteractiveDlq)
        .to(deadLetterExchange)
        .with(MessagingContract.JOBS_INTERACTIVE_QUEUE);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * Work-queue arguments: bounded length with the configured overflow strategy (backpressure) and
   * dead-lettering to {@code orazaka.dlx} keyed by the source queue name (AGENTS.md §6).
   */
  private Map<String, Object> workQueueArguments(String queueName) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("x-max-length", brokerProperties.queue().maxLength());
    String overflow = brokerProperties.queue().overflowStrategy();
    if ("rejectPublish".equals(overflow)) {
      overflow = "reject-publish";
    }
    arguments.put("x-overflow", overflow);
    arguments.put("x-dead-letter-exchange", MessagingContract.DLX_EXCHANGE);
    arguments.put("x-dead-letter-routing-key", queueName);
    return arguments;
  }
}
