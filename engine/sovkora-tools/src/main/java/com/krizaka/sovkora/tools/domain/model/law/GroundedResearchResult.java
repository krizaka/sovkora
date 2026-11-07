package com.krizaka.sovkora.tools.domain.model.law;

import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.GroundedSource;
import java.util.List;
import java.util.Objects;

/**
 * What a grounded call returns: the verified answer, or a refusal that says why. A refusal carries
 * an empty answer: the engine never answers from what it did not read.
 *
 * @param outcome how the call ended
 * @param answer the answer rendered from the verified claims; empty on a refusal
 * @param sources the documents this answer read, from the sources and not from the model
 * @param unknown the {@code instrument ref} pairs with no text in force that day
 */
public record GroundedResearchResult(
    Outcome outcome, GroundedAnswer answer, List<GroundedSource> sources, List<String> unknown) {

  /** How a call ended. */
  public enum Outcome {
    /** The model answered over the sources and the verifier checked every claim. */
    ANSWERED,
    /** {@code research.enabled}, {@code law.corpus.enabled} or the source is off. */
    DISABLED,
    /** A document was given and {@code law.lecture.enabled} is off. */
    LECTURE_DISABLED,
    /** A source other than {@code law-corpus} was asked for. */
    UNSUPPORTED_SOURCE,
    /** No text of the law was in force that day: nothing was asked of the model. */
    NO_SOURCES,
    /**
     * A lecture would cost more than {@code law.lecture.max-credits} (or the cap is 0): nothing was
     * asked of the model and nothing is charged.
     */
    CREDIT_CAP_REACHED,
    /** The run budget could not hold the call. */
    BUDGET_EXHAUSTED
  }

  /** Checks the parts are present. */
  public GroundedResearchResult {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(answer, "answer");
    sources = sources == null ? List.of() : List.copyOf(sources);
    unknown = unknown == null ? List.of() : List.copyOf(unknown);
  }

  /**
   * A refusal: no answer, no source.
   *
   * @param outcome why
   * @param unknown the references with no text that day
   * @return the refusal
   */
  public static GroundedResearchResult refused(Outcome outcome, List<String> unknown) {
    return new GroundedResearchResult(
        outcome, new GroundedAnswer("", List.of(), List.of()), List.of(), unknown);
  }
}
