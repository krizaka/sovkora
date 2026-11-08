package com.krizaka.sovkora.knowledge.infrastructure.config;

/**
 * RabbitMQ topology constants for the knowledge service (AGENTS.md §6). Contract copy — the topic
 * exchanges are shared, stable contracts; this service owns only its {@code orazaka.jobs.rag} queue
 * and its {@code <queue>.dlq}.
 */
final class AmqpConstants {

  private AmqpConstants() {}

  static final String JOBS_EXCHANGE = "orazaka.jobs";
  static final String DLX_EXCHANGE = "orazaka.dlx";

  static final String RAG_QUEUE = "orazaka.jobs.rag";
  static final String RAG_BINDING = "job.rag.*";
  static final String RAG_DLQ = RAG_QUEUE + ".dlq";

  /**
   * Matches the platform work-queue arguments (JobsTopologyConfig) so re-declares are equivalent.
   */
  static final int QUEUE_MAX_LENGTH = 1000;
}
