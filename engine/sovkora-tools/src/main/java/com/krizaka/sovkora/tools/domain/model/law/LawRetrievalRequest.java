package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One retrieval of the law for a kind of document (ADR-089 §2): the jurisdiction, the kind, the
 * question built from the kind, the articles the kind targets, the day the law must be in force.
 *
 * @param jurisdiction the jurisdiction, such as {@code QC}
 * @param kindId the kind of document, such as the one of a visit notice (data of the caller)
 * @param question what the grounded call is asked
 * @param language {@code fr} or {@code en}
 * @param asOf the day the articles must be in force, required: the caller reads it from the
 *     document or from the calendar day in the jurisdiction's zone, never from a clock's own zone
 * @param targets the instruments and articles the kind targets
 * @param runId the run this call is metered under and capped by
 * @param actorId who the call is billed to
 */
public record LawRetrievalRequest(
    String jurisdiction,
    String kindId,
    String question,
    String language,
    LocalDate asOf,
    List<LawTargetRef> targets,
    UUID runId,
    String actorId) {

  /** Checks the request is a complete question. */
  public LawRetrievalRequest {
    requireText(jurisdiction, "jurisdiction");
    requireText(kindId, "kindId");
    requireText(question, "question");
    requireText(language, "language");
    Objects.requireNonNull(runId, "runId");
    requireText(actorId, "actorId");
    if (asOf == null) {
      throw new IllegalArgumentException("asOf is required: the day the law is read at");
    }
    targets = targets == null ? List.of() : List.copyOf(targets);
    if (targets.isEmpty()) {
      throw new IllegalArgumentException("a retrieval targets at least one instrument");
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
