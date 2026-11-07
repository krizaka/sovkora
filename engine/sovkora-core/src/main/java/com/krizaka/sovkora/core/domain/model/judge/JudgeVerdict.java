package com.krizaka.sovkora.core.domain.model.judge;

/**
 * The level-2 judge's answer: whether the output holds, how well (0 = unsupported, 1 = fully
 * supported) and why. {@link #drift()} is what a {@code DriftAdjustment} carries.
 *
 * @param pass whether the output is accepted
 * @param score support in [0, 1]
 * @param feedback the judge's reason, possibly empty
 */
public record JudgeVerdict(boolean pass, float score, String feedback) {

  /** Validates the score and normalises a missing feedback. */
  public JudgeVerdict {
    if (!(score >= 0f && score <= 1f)) {
      throw new IllegalArgumentException("score must be in [0, 1]: " + score);
    }
    feedback = feedback == null ? "" : feedback;
  }

  /**
   * How far the output drifted from what the context supports.
   *
   * @return {@code 1 - score}
   */
  public float drift() {
    return 1f - score;
  }
}
