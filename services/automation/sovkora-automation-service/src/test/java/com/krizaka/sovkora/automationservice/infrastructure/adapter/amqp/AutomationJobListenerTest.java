package com.krizaka.sovkora.automationservice.infrastructure.adapter.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.sovkora.automationservice.application.service.ConnectorDispatcher;
import com.krizaka.sovkora.automationservice.domain.model.AutomationJobPayload;
import com.krizaka.sovkora.automationservice.domain.model.AutomationJobStatus;
import com.krizaka.sovkora.automationservice.infrastructure.config.AmqpConstants;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class AutomationJobListenerTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private static final String STATUS_KEY = "status";

  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private ConnectorDispatcher connectorDispatcher;
  @Mock private MessageDedup messageDedupService;
  private AutomationJobListener listener;

  @BeforeEach
  void setUp() {
    // A claim succeeds unless a test says otherwise: Mockito's default `false` would mean every
    // message is a duplicate, which silently turns every listener test into a skip test.
    // any(), not anyString(): these tests pass a null messageId, which anyString() misses.
    lenient().when(messageDedupService.claim(any(), any())).thenReturn(true);
    listener = new AutomationJobListener(rabbitTemplate, connectorDispatcher, messageDedupService);
  }

  @Test
  @DisplayName("Duplicate messageId is skipped without dispatching")
  void duplicateMessageIsSkipped() {
    when(messageDedupService.claim("automation.jobs", "m-1")).thenReturn(false);

    listener.onJobReceived(createJob(), "m-1");

    verifyNoInteractions(connectorDispatcher, rabbitTemplate);
  }

  @Test
  @DisplayName("Processed message is marked for dedup after its terminal outcome")
  void processedMessageIsMarked() {
    listener.onJobReceived(createJob(), "m-2");

    verify(messageDedupService).claim("automation.jobs", "m-2");
  }

  private AutomationJobPayload createJob() {
    return new AutomationJobPayload(
        "job-100",
        "user-1",
        "JIRA",
        "create-issue",
        AutomationJobStatus.APPROVED,
        Map.of("summary", "Fix bug"),
        Instant.now(FIXED_CLOCK));
  }

  @Test
  @DisplayName("Should publish RUNNING telemetry then delegate to dispatcher on success")
  @SuppressWarnings("unchecked")
  void successfulJobLifecycle() {
    AutomationJobPayload job = createJob();

    listener.onJobReceived(job, null);

    // Verify dispatcher was called
    verify(connectorDispatcher).dispatch(job);

    // Verify 2 telemetry events: RUNNING then COMPLETED
    ArgumentCaptor<Map<String, Object>> telemetryCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate, times(2))
        .convertAndSend(
            eq(AmqpConstants.EVENTS_EXCHANGE),
            eq(AmqpConstants.TELEMETRY_ROUTING_KEY),
            telemetryCaptor.capture());

    // First telemetry = RUNNING
    Map<String, Object> runningTelemetry = telemetryCaptor.getAllValues().getFirst();
    assertThat(runningTelemetry)
        .containsEntry(STATUS_KEY, "RUNNING")
        .containsEntry("jobId", "job-100");

    // Second telemetry = COMPLETED
    Map<String, Object> completedTelemetry = telemetryCaptor.getAllValues().get(1);
    assertThat(completedTelemetry).containsEntry(STATUS_KEY, "COMPLETED");
  }

  @Test
  @DisplayName("Should publish FAILED telemetry when dispatcher throws")
  @SuppressWarnings("unchecked")
  void failedJobPublishesFailedTelemetry() {
    AutomationJobPayload job = createJob();
    doThrow(new RuntimeException("Connector unavailable")).when(connectorDispatcher).dispatch(job);

    listener.onJobReceived(job, null);

    // Verify telemetry: first RUNNING, then FAILED
    ArgumentCaptor<Map<String, Object>> telemetryCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate, times(2))
        .convertAndSend(
            eq(AmqpConstants.EVENTS_EXCHANGE),
            eq(AmqpConstants.TELEMETRY_ROUTING_KEY),
            telemetryCaptor.capture());

    Map<String, Object> failedTelemetry = telemetryCaptor.getAllValues().get(1);
    assertThat(failedTelemetry).containsEntry(STATUS_KEY, "FAILED");
    assertThat((String) failedTelemetry.get("message")).contains("Connector unavailable");
  }

  @Test
  @DisplayName("Should always publish at least 2 telemetry events per job")
  void alwaysPublishesTwoTelemetryEvents() {
    listener.onJobReceived(createJob(), null);

    verify(rabbitTemplate, times(2))
        .convertAndSend(
            eq(AmqpConstants.EVENTS_EXCHANGE),
            eq(AmqpConstants.TELEMETRY_ROUTING_KEY),
            (Object) any());
  }
}
