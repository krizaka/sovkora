package com.krizaka.sovkora.knowledge.infrastructure.config;

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
 * RabbitMQ topology for the knowledge service (AGENTS.md §6): its own {@code orazaka.jobs.rag}
 * queue bound to {@code job.rag.*} on the shared {@code orazaka.jobs} topic exchange,
 * dead-lettering to {@code orazaka.jobs.rag.dlq} through {@code orazaka.dlx}. Exchange declarations
 * are idempotent duplicates of the platform topology (same type/durability) so the service can
 * start first; the queue arguments match {@code JobsTopologyConfig.workQueueArguments}
 * argument-for-argument, or RabbitMQ rejects the re-declare with {@code PRECONDITION_FAILED}.
 */
@Configuration
public class AmqpConfiguration {

  @Bean
  public TopicExchange jobsExchange() {
    return new TopicExchange(AmqpConstants.JOBS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(AmqpConstants.DLX_EXCHANGE, true, false);
  }

  @Bean
  public Queue ragJobsQueue() {
    return new Queue(
        AmqpConstants.RAG_QUEUE, true, false, false, workQueueArguments(AmqpConstants.RAG_QUEUE));
  }

  @Bean
  public Binding ragJobsBinding(Queue ragJobsQueue, TopicExchange jobsExchange) {
    return BindingBuilder.bind(ragJobsQueue).to(jobsExchange).with(AmqpConstants.RAG_BINDING);
  }

  @Bean
  public Queue ragJobsDlq() {
    return new Queue(AmqpConstants.RAG_DLQ, true, false, false);
  }

  @Bean
  public Binding ragJobsDlqBinding(Queue ragJobsDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(ragJobsDlq).to(deadLetterExchange).with(AmqpConstants.RAG_QUEUE);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  private static Map<String, Object> workQueueArguments(String queueName) {
    return Map.of(
        "x-max-length",
        AmqpConstants.QUEUE_MAX_LENGTH,
        "x-overflow",
        "reject-publish",
        "x-dead-letter-exchange",
        AmqpConstants.DLX_EXCHANGE,
        "x-dead-letter-routing-key",
        queueName);
  }
}
