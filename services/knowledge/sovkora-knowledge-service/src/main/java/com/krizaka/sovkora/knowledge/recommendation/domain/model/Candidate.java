package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * An item that retrieval brought in, with the raw signals ranking turns into a score.
 *
 * @param itemId the item
 * @param creatorId its creator (for the creator cap)
 * @param publishedAt when it was published (freshness)
 * @param vector its embedding, when it has one (similarity, MMR)
 * @param similarity cosine to the person's profile (or to the source item), 0 when unknown
 * @param coEngagement how many peers engaged it (raw count)
 * @param popularity weighted positive events of the window (raw)
 * @param followed it is from a followed creator
 * @param sources the retrieval sources that proposed it
 */
public record Candidate(
    String itemId,
    String creatorId,
    Instant publishedAt,
    Vector vector,
    double similarity,
    double coEngagement,
    double popularity,
    boolean followed,
    Set<Reason> sources) {

  public Candidate {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(creatorId, "creatorId");
    Objects.requireNonNull(publishedAt, "publishedAt");
    sources = sources == null || sources.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(sources));
  }
}
