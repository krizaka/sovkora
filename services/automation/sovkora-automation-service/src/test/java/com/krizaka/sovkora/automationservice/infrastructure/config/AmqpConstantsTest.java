package com.krizaka.sovkora.automationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmqpConstantsTest {

  @Test
  @DisplayName("Exchanges follow the AGENTS.md §6 topology (orazaka.jobs / orazaka.events)")
  void exchangeNames() {
    assertThat(AmqpConstants.JOBS_EXCHANGE).isEqualTo("orazaka.jobs");
    assertThat(AmqpConstants.EVENTS_EXCHANGE).isEqualTo("orazaka.events");
    assertThat(AmqpConstants.DLX_EXCHANGE).isEqualTo("orazaka.dlx");
  }

  @Test
  @DisplayName("Automation queue binds job.automation.* and dead-letters to <queue>.dlq")
  void automationQueueTopology() {
    assertThat(AmqpConstants.AUTOMATION_QUEUE).isEqualTo("orazaka.jobs.automation");
    assertThat(AmqpConstants.AUTOMATION_BINDING).isEqualTo("job.automation.*");
    assertThat(AmqpConstants.AUTOMATION_DLQ).isEqualTo(AmqpConstants.AUTOMATION_QUEUE + ".dlq");
  }

  @Test
  @DisplayName("The Studio run outcomes are heard on their own queue, with its dead-letter queue")
  void studioRunOutcomes() {
    assertThat(AmqpConstants.STUDIO_RUN_SUCCEEDED).isEqualTo("evt.studio.run.succeeded");
    assertThat(AmqpConstants.STUDIO_RUN_FAILED).isEqualTo("evt.studio.run.failed");
    assertThat(AmqpConstants.STUDIO_RUNS_QUEUE).isEqualTo("orazaka.automation.studio-runs");
    assertThat(AmqpConstants.STUDIO_RUNS_DLQ).isEqualTo(AmqpConstants.STUDIO_RUNS_QUEUE + ".dlq");
  }

  @Test
  @DisplayName("Published keys follow evt.{aggregate}.{type} / job.{capability}.{action}")
  void publishedKeyPatterns() {
    assertThat(AmqpConstants.TELEMETRY_ROUTING_KEY).isEqualTo("evt.automation.telemetry");
    assertThat(AmqpConstants.AGENT_DISPATCH_PREFIX).isEqualTo("job.agent.dispatch.");
  }

  @Test
  @DisplayName("AmqpConstants private constructor should prevent instantiation")
  void notInstantiable() throws Exception {
    var ctor = AmqpConstants.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
  }
}
