package com.krizaka.sovkora.tools.domain.model.research;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of {@code research_fetch}: who read what host, for which run, with what outcome. The
 * path is a hash and the query is not kept (ADR-064): the audit says that a page was read, never
 * which words were looked up.
 *
 * @param at when
 * @param actorId the actor, or {@code null}
 * @param runId the Studio run, or {@code null}
 * @param chatId the conversation, or {@code null}
 * @param sourceId the source, or {@code null} when unknown
 * @param operation {@code FETCH} or {@code ROBOTS} in this slice
 * @param host the host
 * @param pathHash SHA-256 (hex) of the path and query, or {@code null}
 * @param httpStatus the status, or {@code null} when no answer
 * @param bytes the body size, or {@code null}
 * @param durationMs the time spent, or {@code null}
 * @param cacheHit whether it was served from the cache
 * @param refusal why it was refused, or {@code null}
 */
public record FetchAudit(
    Instant at,
    String actorId,
    UUID runId,
    String chatId,
    UUID sourceId,
    String operation,
    String host,
    String pathHash,
    Integer httpStatus,
    Integer bytes,
    Integer durationMs,
    boolean cacheHit,
    FetchRefusal refusal) {

  /** Validates the line. */
  public FetchAudit {
    Objects.requireNonNull(at, "at must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
