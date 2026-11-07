package com.krizaka.sovkora.persistence.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagingContractTest {

  @Test
  @DisplayName("Exchanges follow the AGENTS.md §6 topology (orazaka.jobs / orazaka.events)")
  void exchangeNames() {
    assertThat(MessagingContract.JOBS_EXCHANGE).isEqualTo("orazaka.jobs");
    assertThat(MessagingContract.EVENTS_EXCHANGE).isEqualTo("orazaka.events");
    assertThat(MessagingContract.DLX_EXCHANGE).isEqualTo("orazaka.dlx");
  }

  @Test
  @DisplayName("Job command keys follow job.{capability}.{action}")
  void jobCommandKeys() {
    assertThat(MessagingContract.JOB_MEDIA_GENERATE).isEqualTo("job.media.generate");
    assertThat(MessagingContract.JOB_VIDEO_GENERATE).isEqualTo("job.video.generate");
    assertThat(MessagingContract.JOB_TEXT_PROCESS).isEqualTo("job.text.process");
    assertThat(MessagingContract.JOB_AUTOMATION_APPROVED).isEqualTo("job.automation.approved");
    assertThat(MessagingContract.JOB_AGENT_DISPATCH_PREFIX).isEqualTo("job.agent.dispatch.");
  }

  @Test
  @DisplayName("Domain event keys follow evt.{aggregate}.{type}")
  void domainEventKeys() {
    assertThat(MessagingContract.EVT_USER_REGISTERED).isEqualTo("evt.user.registered");
    assertThat(MessagingContract.EVT_PASSWORD_RESET).isEqualTo("evt.password.reset");
    assertThat(MessagingContract.EVT_AGENT_PRESENCE).isEqualTo("evt.agent.presence");
    assertThat(MessagingContract.EVT_AGENT_RESULT_PREFIX).isEqualTo("evt.agent.result.");
  }

  @Test
  @DisplayName("Job lifecycle event keys follow job.{jobId}.progress|done|error")
  void jobLifecycleKeys() {
    assertThat(MessagingContract.jobProgressKey("j-1")).isEqualTo("job.j-1.progress");
    assertThat(MessagingContract.jobDoneKey("j-1")).isEqualTo("job.j-1.done");
    assertThat(MessagingContract.jobErrorKey("j-1")).isEqualTo("job.j-1.error");
  }

  @Test
  @DisplayName("Every queue has its <queue>.dlq (AGENTS.md §6)")
  void dlqNamesDeriveFromQueueNames() {
    assertThat(MessagingContract.JOBS_BATCH_DLQ)
        .isEqualTo(MessagingContract.JOBS_BATCH_QUEUE + ".dlq");
    assertThat(MessagingContract.JOBS_INTERACTIVE_DLQ)
        .isEqualTo(MessagingContract.JOBS_INTERACTIVE_QUEUE + ".dlq");
    assertThat(MessagingContract.EVENTS_JOB_RELAY_DLQ)
        .isEqualTo(MessagingContract.EVENTS_JOB_RELAY_QUEUE + ".dlq");
  }

  @Test
  @DisplayName("Queue bindings cover the keys their producers emit")
  void bindingsMatchEmittedKeys() {
    assertThat(MessagingContract.JOB_MEDIA_GENERATE).startsWith("job.media.");
    assertThat(MessagingContract.JOBS_BATCH_BINDING).isEqualTo("job.media.generate");
    assertThat(MessagingContract.JOBS_INTERACTIVE_MEDIA_BINDING).isEqualTo("job.media.analyze");
    assertThat(MessagingContract.JOB_VIDEO_GENERATE).startsWith("job.video.");
    assertThat(MessagingContract.JOBS_VIDEO_BINDING).isEqualTo("job.video.*");
    assertThat(MessagingContract.JOB_TEXT_PROCESS).startsWith("job.text.");
    assertThat(MessagingContract.JOBS_INTERACTIVE_TEXT_BINDING).isEqualTo("job.text.*");
    assertThat(MessagingContract.EVENTS_JOB_RELAY_BINDING).isEqualTo("job.#");
  }

  @Test
  @DisplayName("MessagingContract private constructor should prevent instantiation")
  void notInstantiable() throws Exception {
    var ctor = MessagingContract.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
  }
}
