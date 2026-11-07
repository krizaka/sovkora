package com.krizaka.sovkora.tools.domain.model.law;

import com.krizaka.sovkora.core.domain.model.research.Claim;
import java.util.List;
import java.util.Objects;

/**
 * What a retrieval answers: the articles used and the claims on them, each {@code VERIFIED} or
 * {@code TO_VALIDATE} (only the verified ones are used by the derivation stage).
 *
 * @param outcome how the answer came about
 * @param articles the articles the claims are about
 * @param claims the claims in the ADR-087 shape, verified against the articles
 * @param unknown the references the corpus holds no version of for the day asked ({@code
 *     LAW_VERSION_UNKNOWN}), as {@code instrument ref}
 */
public record LawRetrievalResult(
    Outcome outcome, List<CitedArticle> articles, List<Claim> claims, List<String> unknown) {

  /** How a retrieval ended. */
  public enum Outcome {
    /** The corpus or grounded research is off: nothing was read and no model was called. */
    DISABLED,
    /** The corpus holds none of the articles for that day: nothing to ground on. */
    NO_ARTICLES,
    /** Served from {@code law_retrieval}: no model call, no charge. */
    CACHED,
    /** Computed now through one metered grounded call, and cached. */
    RETRIEVED,
    /** The run budget could not hold the call: nothing was asked, nothing cached. */
    BUDGET_EXHAUSTED
  }

  /** Copies the lists and checks the outcome. */
  public LawRetrievalResult {
    Objects.requireNonNull(outcome, "outcome");
    articles = articles == null ? List.of() : List.copyOf(articles);
    claims = claims == null ? List.of() : List.copyOf(claims);
    unknown = unknown == null ? List.of() : List.copyOf(unknown);
  }

  /**
   * An answer that read nothing and asked nothing.
   *
   * @param outcome why
   * @param unknown the references with no version for that day
   * @return the empty answer
   */
  public static LawRetrievalResult empty(Outcome outcome, List<String> unknown) {
    return new LawRetrievalResult(outcome, List.of(), List.of(), unknown);
  }
}
