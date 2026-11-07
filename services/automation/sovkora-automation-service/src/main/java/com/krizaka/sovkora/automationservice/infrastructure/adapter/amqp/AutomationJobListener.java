package com.krizaka.sovkora.automationservice.infrastructure.adapter.amqp;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.sovkora.automationservice.application.service.ConnectorDispatcher;
import com.krizaka.sovkora.automationservice.domain.model.AutomationJobPayload;
import com.krizaka.sovkora.automationservice.domain.model.AutomationJobStatus;
import com.krizaka.sovkora.automationservice.infrastructure.config.AmqpConstants;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Consumes approved automation jobs from RabbitMQ and dispatches them. */
@Component
public class AutomationJobListener {
  private static final Logger logger = LoggerFactory.getLogger(AutomationJobListener.class);

  /** Stable dedup identity of this consumer (AGENTS.md §6 messageId idempotency). */
  private static final String DEDUP_CONSUMER = "automation.jobs";

  private final RabbitTemplate rabbitTemplate;
  private final ConnectorDispatcher connectorDispatcher;
  private final MessageDedup messageDedupService;

  public AutomationJobListener(
      RabbitTemplate rabbitTemplate,
      ConnectorDispatcher connectorDispatcher,
      MessageDedup messageDedupService) {
    this.rabbitTemplate = rabbitTemplate;
    this.connectorDispatcher = connectorDispatcher;
    this.messageDedupService = messageDedupService;
  }

  @RabbitListener(queues = AmqpConstants.AUTOMATION_QUEUE)
  public void onJobReceived(
      AutomationJobPayload job,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    if (!messageDedupService.claim(DEDUP_CONSUMER, messageId)) {
      logger.info("Skipping duplicate automation message {} for job {}", messageId, job.jobId());
      return;
    }
    logger.info(
        "Received approved job: id={}, connector={}, action={}",
        job.jobId(),
        job.connectorType(),
        job.action());

    publishTelemetry(job.jobId(), AutomationJobStatus.RUNNING, "Job execution started");

    try {
      connectorDispatcher.dispatch(job);
      publishTelemetry(job.jobId(), AutomationJobStatus.COMPLETED, "Job completed successfully");
      logger.info("Job {} completed successfully.", job.jobId());
    } catch (Exception e) {
      publishTelemetry(job.jobId(), AutomationJobStatus.FAILED, "Error: " + e.getMessage());
      logger.error("Job {} failed.", job.jobId(), e);
    }
  }

  private void publishTelemetry(String jobId, AutomationJobStatus status, String message) {
    Map<String, Object> telemetry =
        Map.of("jobId", jobId, "status", status.name(), "message", message);
    rabbitTemplate.convertAndSend(
        AmqpConstants.EVENTS_EXCHANGE, AmqpConstants.TELEMETRY_ROUTING_KEY, telemetry);
  }
}
