package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One call of the {@code orazaka.core.research.grounded} capability (ADR-087 §1): the inputs a
 * blueprint step sends, named as the capability's input schema names them.
 *
 * @param sources the research sources to read; only {@code law-corpus} is served so far
 * @param jurisdiction the jurisdiction of the law, such as {@code QC}
 * @param asOf the day the law must be in force; required, never read from a clock (ADR-089 §2)
 * @param language {@code fr} or {@code en}
 * @param question what is asked
 * @param targets the instruments and articles to read
 * @param document the user's own document, given as data for a lecture (ADR-089 §6), or null
 * @param runId the run the call is metered under
 * @param actorId who the call is billed to
 */
public record GroundedResearchRequest(
    List<String> sources,
    String jurisdiction,
    LocalDate asOf,
    String language,
    String question,
    List<LawTargetRef> targets,
    String document,
    UUID runId,
    String actorId) {

  /** Checks the request is a complete question. */
  public GroundedResearchRequest {
    sources = sources == null ? List.of() : List.copyOf(sources);
    targets = targets == null ? List.of() : List.copyOf(targets);
    Objects.requireNonNull(question, "question");
    if (question.isBlank()) {
      throw new IllegalArgumentException("question is required");
    }
    language = language == null ? "fr" : language.trim().toLowerCase(Locale.ROOT);
    if (!"fr".equals(language) && !"en".equals(language)) {
      throw new IllegalArgumentException("language must be fr or en");
    }
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(actorId, "actorId");
    if (actorId.isBlank()) {
      throw new IllegalArgumentException("actorId is required");
    }
    document = document == null || document.isBlank() ? null : document;
  }

  /**
   * Whether this call reads a document of the user's (a lecture).
   *
   * @return true when a document is given
   */
  public boolean isLecture() {
    return document != null;
  }
}
