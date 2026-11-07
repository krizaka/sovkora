package com.krizaka.sovkora.knowledge.infrastructure.adapter.amqp;

import java.util.Objects;

/**
 * Wire shape of a {@code job.rag.*} indexing command (contract copy — no shared jar across the
 * service boundary). Unknown fields in the payload are ignored by the JSON converter.
 *
 * @param toolId the tool whose RAG sources should be indexed
 */
public record RagIndexCommand(String toolId) {

  public RagIndexCommand {
    Objects.requireNonNull(toolId, "toolId is required");
  }
}
