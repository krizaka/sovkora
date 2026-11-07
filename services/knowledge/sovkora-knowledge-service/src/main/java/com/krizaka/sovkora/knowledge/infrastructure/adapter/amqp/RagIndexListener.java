package com.krizaka.sovkora.knowledge.infrastructure.adapter.amqp;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.sovkora.knowledge.application.service.IngestionService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code job.rag.*} indexing commands from {@code orazaka.jobs.rag} (AGENTS.md §6) and
 * delegates to {@link IngestionService}. Deduplicated by AMQP {@code messageId}; the message is
 * marked processed only after a terminal outcome, so a crash mid-ingestion lets the redelivery run
 * again (at-least-once).
 */
@Component
public class RagIndexListener {

  private static final Logger logger = LoggerFactory.getLogger(RagIndexListener.class);
  private static final String DEDUP_CONSUMER = "knowledge.rag-index";

  private final IngestionService ingestionService;
  private final MessageDedup messageDedupService;

  public RagIndexListener(IngestionService ingestionService, MessageDedup messageDedupService) {
    this.ingestionService = Objects.requireNonNull(ingestionService, "IngestionService required");
    this.messageDedupService = Objects.requireNonNull(messageDedupService, "MessageDedup required");
  }

  @RabbitListener(queues = "orazaka.jobs.rag")
  public void onIndexCommand(
      RagIndexCommand command,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    if (!messageDedupService.claim(DEDUP_CONSUMER, messageId)) {
      logger.info("Skipping duplicate RAG index message {}", messageId);
      return;
    }
    logger.info("Received RAG index command for tool {}", command.toolId());
    try {
      ingestionService.ingest(command.toolId());
    } catch (RuntimeException failed) {
      // Or a tool stays unindexed for good: the redelivery is refused by the claim its own failed
      // ingestion left behind (ADR-067, audit #30). Re-ingesting is safe — indexing is keyed by
      // tool id and replaces what it wrote.
      messageDedupService.release(DEDUP_CONSUMER, messageId);
      throw failed;
    }
  }
}
