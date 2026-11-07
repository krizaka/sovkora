package com.krizaka.sovkora.core.domain.model.research;

import java.util.List;
import java.util.Objects;

/**
 * A grounded answer in the citation shape of ADR-087 (also the output contract of {@code
 * orazaka.core.research.grounded}, sovkora-studio-service#77).
 *
 * @param answer the prose answer
 * @param claims its factual claims, each with its citations
 * @param sources what was read
 */
public record GroundedAnswer(String answer, List<Claim> claims, List<GroundedSource> sources) {

  /** Validates the answer. */
  public GroundedAnswer {
    Objects.requireNonNull(answer, "answer must not be null");
    claims = claims == null ? List.of() : List.copyOf(claims);
    sources = sources == null ? List.of() : List.copyOf(sources);
  }
}
