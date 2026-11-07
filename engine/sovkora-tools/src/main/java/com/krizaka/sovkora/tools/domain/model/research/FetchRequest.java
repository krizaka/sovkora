package com.krizaka.sovkora.tools.domain.model.research;

import java.util.Objects;
import java.util.UUID;

/**
 * One page to read for grounded research, through one administrator-declared source.
 *
 * @param sourceKey the {@code research_source.source_key} the fetch goes through
 * @param url the absolute URL to read
 * @param actorId who the research is for (audit), or {@code null} for the platform
 * @param runId the Studio run it serves, or {@code null}
 * @param chatId the conversation it serves, or {@code null}
 */
public record FetchRequest(
    String sourceKey, String url, String actorId, UUID runId, String chatId) {

  /** Validates the request. */
  public FetchRequest {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(url, "url must not be null");
    if (sourceKey.isBlank() || url.isBlank()) {
      throw new IllegalArgumentException("sourceKey and url must not be blank");
    }
  }
}
