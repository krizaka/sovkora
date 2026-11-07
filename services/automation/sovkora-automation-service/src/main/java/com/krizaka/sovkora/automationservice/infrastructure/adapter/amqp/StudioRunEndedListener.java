package com.krizaka.sovkora.automationservice.infrastructure.adapter.amqp;

import com.krizaka.sovkora.automationservice.application.service.RunOutcomeService;
import com.krizaka.sovkora.automationservice.infrastructure.config.AmqpConstants;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Closes the automation runs that wait on a Studio run: the Studio service announces every run's
 * end on {@code orazaka.events} ({@code evt.studio.run.succeeded|failed}, AGENTS.md §6). The update
 * is idempotent by construction (only a running run changes), so a redelivery is harmless and no
 * dedup row is needed.
 */
@Component
class StudioRunEndedListener {

  private static final Logger logger = LoggerFactory.getLogger(StudioRunEndedListener.class);

  private final RunOutcomeService outcomes;

  StudioRunEndedListener(RunOutcomeService outcomes) {
    this.outcomes = Objects.requireNonNull(outcomes, "RunOutcomeService cannot be null");
  }

  @RabbitListener(queues = AmqpConstants.STUDIO_RUNS_QUEUE)
  void onStudioRunEnded(
      Map<String, Object> event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
    Object runId = event == null ? null : event.get("runId");
    if (runId == null) {
      logger.warn("a Studio run event without a run id was dropped");
      return;
    }
    try {
      outcomes.studioRunEnded(
          UUID.fromString(runId.toString()), AmqpConstants.STUDIO_RUN_SUCCEEDED.equals(routingKey));
    } catch (IllegalArgumentException notAnId) {
      logger.warn("a Studio run event with an unreadable run id was dropped");
    }
  }
}
