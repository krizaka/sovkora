package com.krizaka.sovkora.tools.domain.model.law;

import java.io.Serial;
import java.util.Locale;
import java.util.Objects;

/**
 * A review or promotion request the rules of stage 5 refuse (ADR-089 §5, §8). It names its reason
 * as a closed code, so the host answers it without reading a message: {@link Code#httpStatus()}.
 */
public final class LawReviewRefusal extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Why a request was refused. */
  public enum Code {
    /** No proposal has this id. */
    PROPOSAL_NOT_FOUND(404),
    /** The pack has no such kind. */
    KIND_NOT_FOUND(404),
    /** Approve needs {@code PROPOSED}; reject needs {@code PROPOSED} or {@code APPROVED}. */
    PROPOSAL_NOT_REVIEWABLE(409),
    /** A promoted or superseded revision is not edited: edit the newest one. */
    PROPOSAL_NOT_EDITABLE(409),
    /** Promotion takes only {@code APPROVED} proposals. */
    PROPOSAL_NOT_APPROVED(409),
    /** A proposal is already in a packs pull request. */
    ALREADY_IN_REVIEW(409),
    /** A proposal is of another kind than the one promoted. */
    KIND_MISMATCH(409),
    /** Two proposals of the same rule in one promotion. */
    DUPLICATE_RULE(409),
    /** The kind has fewer golden cases than {@code law.review.min-golden-cases}. */
    GOLDEN_MINIMUM_NOT_MET(409),
    /** A pinned hash is not the version in force on the ruleset's day. */
    LAW_VERSION_MISMATCH(409),
    /** A quote is longer than a ruleset's law block holds. */
    QUOTE_TOO_LONG(409),
    /** No rulesets repository is configured: promotion is off. */
    LAW_PROMOTION_OFF(503),
    /** No derivation is wired. */
    LAW_DERIVATION_OFF(503);

    private final int httpStatus;

    Code(int httpStatus) {
      this.httpStatus = httpStatus;
    }

    /**
     * The HTTP status a host answers it with.
     *
     * @return 404, 409 or 503
     */
    public int httpStatus() {
      return httpStatus;
    }

    /**
     * The code as a slug, such as {@code golden-minimum-not-met}.
     *
     * @return the slug
     */
    public String slug() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  private final Code code;

  /**
   * Creates a refusal.
   *
   * @param code why
   * @param detail what, for the reviewer
   */
  public LawReviewRefusal(Code code, String detail) {
    super(detail);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Why it was refused.
   *
   * @return the code
   */
  public Code code() {
    return code;
  }
}
