package com.krizaka.sovkora.knowledge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class AmqpConfigurationTest {

  private final AmqpConfiguration config = new AmqpConfiguration();

  @Test
  @DisplayName("The RAG queue is durable and dead-letters with the platform work-queue arguments")
  void ragQueueMatchesPlatformArguments() {
    Queue queue = config.ragJobsQueue();
    assertThat(queue.getName()).isEqualTo("orazaka.jobs.rag");
    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-max-length", 1000)
        .containsEntry("x-overflow", "reject-publish")
        .containsEntry("x-dead-letter-exchange", "orazaka.dlx")
        .containsEntry("x-dead-letter-routing-key", "orazaka.jobs.rag");
  }

  @Test
  @DisplayName("The RAG queue binds to job.rag.* and its DLQ to the queue name on the DLX")
  void ragBindings() {
    Binding binding = config.ragJobsBinding(config.ragJobsQueue(), config.jobsExchange());
    assertThat(binding.getRoutingKey()).isEqualTo("job.rag.*");

    Binding dlqBinding = config.ragJobsDlqBinding(config.ragJobsDlq(), config.deadLetterExchange());
    assertThat(dlqBinding.getDestination()).isEqualTo("orazaka.jobs.rag.dlq");
    assertThat(dlqBinding.getRoutingKey()).isEqualTo("orazaka.jobs.rag");
  }
}
