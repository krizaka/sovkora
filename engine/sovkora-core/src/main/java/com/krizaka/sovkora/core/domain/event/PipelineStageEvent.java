package com.krizaka.sovkora.core.domain.event;

import java.util.Objects;

/**
 * Immutable Spring Application Event emitted as each interceptor in the prompt pipeline finishes
 * executing, so an outer adapter (the Router's SSE relay) can stream live pipeline progress to the
 * UI without coupling the core to any transport.
 *
 * <p>Published synchronously, in-process, on the request thread — never through the broker (chat is
 * the synchronous interactive path). Correlated downstream by {@code conversationId}.
 *
 * @param conversationId The conversation this stage belongs to (used to route the SSE event).
 * @param interceptorId The interceptor class simple name (matches the pipeline-ack schema ids).
 */
public record PipelineStageEvent(String conversationId, String interceptorId) {

  /** Compact constructor enforcing non-null invariants. */
  public PipelineStageEvent {
    Objects.requireNonNull(conversationId, "conversationId must not be null");
    Objects.requireNonNull(interceptorId, "interceptorId must not be null");
  }
}
