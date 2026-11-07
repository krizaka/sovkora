package com.krizaka.sovkora.persistence.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

class JobsTopologyConfigTest {

  private JobsTopologyConfig config;

  @BeforeEach
  void setUp() {
    config =
        new JobsTopologyConfig(
            new BrokerProperties(new BrokerProperties.QueueProperties(500, "rejectPublish")));
  }

  @Test
  @DisplayName("Constructor rejects null BrokerProperties")
  void constructorRejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new JobsTopologyConfig(null));
  }

  @Test
  @DisplayName("orazaka.jobs is a durable, non-auto-delete topic exchange")
  void jobsExchangeIsDurableTopic() {
    var exchange = config.jobsExchange();

    assertThat(exchange.getName()).isEqualTo(MessagingContract.JOBS_EXCHANGE);
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }

  @Test
  @DisplayName("Work queues are bounded and dead-letter to orazaka.dlx keyed by queue name")
  void workQueuesCarryBackpressureAndDlqArguments() {
    Queue batch = config.jobsBatchQueue();
    Queue video = config.jobsVideoQueue();
    assertThat(video.getName()).isEqualTo(MessagingContract.JOBS_VIDEO_QUEUE);
    assertThat(video.getArguments())
        .containsEntry("x-dead-letter-routing-key", MessagingContract.JOBS_VIDEO_QUEUE);
    Queue interactive = config.jobsInteractiveQueue();

    assertThat(batch.getName()).isEqualTo(MessagingContract.JOBS_BATCH_QUEUE);
    assertThat(batch.getArguments())
        .containsEntry("x-max-length", 500)
        .containsEntry("x-overflow", "reject-publish")
        .containsEntry("x-dead-letter-exchange", MessagingContract.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", MessagingContract.JOBS_BATCH_QUEUE);
    assertThat(interactive.getName()).isEqualTo(MessagingContract.JOBS_INTERACTIVE_QUEUE);
    assertThat(interactive.getArguments())
        .containsEntry("x-dead-letter-routing-key", MessagingContract.JOBS_INTERACTIVE_QUEUE);
  }

  @Test
  @DisplayName("Queues bind job.{capability}.* patterns on orazaka.jobs")
  void queueBindingsUseContractPatterns() {
    Binding batch = config.jobsBatchBinding(config.jobsBatchQueue(), config.jobsExchange());
    Binding video = config.jobsVideoBinding(config.jobsVideoQueue(), config.jobsExchange());
    assertThat(video.getRoutingKey()).isEqualTo(MessagingContract.JOBS_VIDEO_BINDING);
    Binding interactiveText =
        config.jobsInteractiveTextBinding(config.jobsInteractiveQueue(), config.jobsExchange());
    Binding interactiveMedia =
        config.jobsInteractiveMediaBinding(config.jobsInteractiveQueue(), config.jobsExchange());

    assertThat(batch.getExchange()).isEqualTo(MessagingContract.JOBS_EXCHANGE);
    assertThat(batch.getRoutingKey()).isEqualTo(MessagingContract.JOBS_BATCH_BINDING);
    assertThat(interactiveText.getRoutingKey())
        .isEqualTo(MessagingContract.JOBS_INTERACTIVE_TEXT_BINDING);
    // Two bindings on one queue: a lane is a property of the work, not of the media type
    // (ADR-067). Without this one, image analysis would have no consumer at all.
    assertThat(interactiveMedia.getRoutingKey())
        .isEqualTo(MessagingContract.JOBS_INTERACTIVE_MEDIA_BINDING);
    assertThat(interactiveMedia.getDestination())
        .isEqualTo(MessagingContract.JOBS_INTERACTIVE_QUEUE);
  }

  @Test
  @DisplayName("DLQs bind to orazaka.dlx with their source queue name as key")
  void dlqBindingsKeyedBySourceQueue() {
    Binding batchDlq =
        config.jobsBatchDlqBinding(config.jobsBatchDlq(), config.deadLetterExchange());
    Binding interactiveDlq =
        config.jobsInteractiveDlqBinding(config.jobsInteractiveDlq(), config.deadLetterExchange());

    assertThat(batchDlq.getExchange()).isEqualTo(MessagingContract.DLX_EXCHANGE);
    assertThat(batchDlq.getDestination()).isEqualTo(MessagingContract.JOBS_BATCH_DLQ);
    assertThat(batchDlq.getRoutingKey()).isEqualTo(MessagingContract.JOBS_BATCH_QUEUE);
    assertThat(interactiveDlq.getDestination()).isEqualTo(MessagingContract.JOBS_INTERACTIVE_DLQ);
    assertThat(interactiveDlq.getRoutingKey()).isEqualTo(MessagingContract.JOBS_INTERACTIVE_QUEUE);
  }

  @Test
  @DisplayName("JSON converter is configured for cross-module DTO payloads")
  void jsonConverterConfigured() {
    assertThat(config.jsonMessageConverter()).isInstanceOf(JacksonJsonMessageConverter.class);
  }
}
