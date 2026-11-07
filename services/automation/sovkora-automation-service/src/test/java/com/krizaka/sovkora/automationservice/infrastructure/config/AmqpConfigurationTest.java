package com.krizaka.sovkora.automationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

class AmqpConfigurationTest {

  private AmqpConfiguration config;

  @BeforeEach
  void setUp() {
    config = new AmqpConfiguration();
  }

  @Test
  @DisplayName("Should declare the shared topic exchanges and DLX, durable and non-auto-delete")
  void exchangesAreDurableAndNonAutoDelete() {
    TopicExchange jobs = config.jobsExchange();
    TopicExchange events = config.eventsExchange();
    DirectExchange dlx = config.deadLetterExchange();

    assertThat(jobs.getName()).isEqualTo(AmqpConstants.JOBS_EXCHANGE);
    assertThat(jobs.isDurable()).isTrue();
    assertThat(jobs.isAutoDelete()).isFalse();
    assertThat(events.getName()).isEqualTo(AmqpConstants.EVENTS_EXCHANGE);
    assertThat(events.isDurable()).isTrue();
    assertThat(events.isAutoDelete()).isFalse();
    assertThat(dlx.getName()).isEqualTo(AmqpConstants.DLX_EXCHANGE);
    assertThat(dlx.isDurable()).isTrue();
  }

  @Test
  @DisplayName("Automation queue is durable and dead-letters to orazaka.dlx keyed by queue name")
  void automationQueueIsDurableWithDlqArguments() {
    Queue queue = config.automationJobsQueue();

    assertThat(queue.getName()).isEqualTo(AmqpConstants.AUTOMATION_QUEUE);
    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", AmqpConstants.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", AmqpConstants.AUTOMATION_QUEUE);
  }

  @Test
  @DisplayName("Automation queue binds job.automation.* on orazaka.jobs; DLQ binds on the DLX")
  void automationBindingsUseContractKeys() {
    Binding binding =
        config.automationJobsBinding(config.automationJobsQueue(), config.jobsExchange());
    Binding dlqBinding =
        config.automationJobsDlqBinding(config.automationJobsDlq(), config.deadLetterExchange());

    assertThat(binding.getExchange()).isEqualTo(AmqpConstants.JOBS_EXCHANGE);
    assertThat(binding.getDestination()).isEqualTo(AmqpConstants.AUTOMATION_QUEUE);
    assertThat(binding.getRoutingKey()).isEqualTo(AmqpConstants.AUTOMATION_BINDING);
    assertThat(dlqBinding.getExchange()).isEqualTo(AmqpConstants.DLX_EXCHANGE);
    assertThat(dlqBinding.getDestination()).isEqualTo(AmqpConstants.AUTOMATION_DLQ);
    assertThat(dlqBinding.getRoutingKey()).isEqualTo(AmqpConstants.AUTOMATION_QUEUE);
  }

  @Test
  @DisplayName(
      "The Studio run outcomes: succeeded and failed only, never run.started, dead-lettered")
  void studioRunOutcomeBindings() {
    Queue queue = config.studioRunsQueue();
    Binding ok = config.studioRunSucceededBinding(queue, config.eventsExchange());
    Binding ko = config.studioRunFailedBinding(queue, config.eventsExchange());

    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", AmqpConstants.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", AmqpConstants.STUDIO_RUNS_QUEUE);
    assertThat(ok.getExchange()).isEqualTo(AmqpConstants.EVENTS_EXCHANGE);
    assertThat(ok.getRoutingKey()).isEqualTo("evt.studio.run.succeeded");
    assertThat(ko.getRoutingKey()).isEqualTo("evt.studio.run.failed");
    Binding dlq = config.studioRunsDlqBinding(config.studioRunsDlq(), config.deadLetterExchange());
    assertThat(dlq.getDestination()).isEqualTo(AmqpConstants.STUDIO_RUNS_DLQ);
    assertThat(dlq.getRoutingKey()).isEqualTo(AmqpConstants.STUDIO_RUNS_QUEUE);
  }

  @Test
  @DisplayName("Should configure JacksonJsonMessageConverter for JSON serialization")
  void jsonMessageConverterConfigured() {
    MessageConverter converter = config.jsonMessageConverter();

    assertThat(converter).isInstanceOf(JacksonJsonMessageConverter.class);
  }
}
