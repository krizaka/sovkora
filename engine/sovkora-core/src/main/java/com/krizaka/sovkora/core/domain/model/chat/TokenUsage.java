package com.krizaka.sovkora.core.domain.model.chat;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * What one inference actually consumed, in tokens.
 *
 * <p>A typed record rather than three entries in a metadata map (ERR-127): this is the number chat
 * is billed on (ADR-033 §7, {@code KILOTOKEN}), and a value that decides what a user is charged
 * must not be reachable only by string key, where a rename is a silent revenue change and an absent
 * key is indistinguishable from zero consumption.
 *
 * <p>{@link #reported()} is that distinction made explicit: a provider that returns no usage block
 * is unmeasured, which releases the hold, while a genuine zero is measured and settles at the
 * pricebook's floor.
 *
 * @param promptTokens tokens in the prompt sent to the model
 * @param completionTokens tokens the model generated
 * @param totalTokens the provider's own total, which is not always the sum — some providers count
 *     cached or reasoning tokens that appear in neither half, so it is recorded as reported rather
 *     than re-derived
 * @param cachedReadTokens the part of the prompt the provider served from its prompt cache (Spring
 *     AI's {@code Usage#getCacheReadInputTokens}), zero when none or not reported; informational —
 *     it is already inside {@code promptTokens}/{@code totalTokens}, so it never changes the bill
 * @param cacheWriteTokens the part of the prompt the provider wrote to its prompt cache (Spring
 *     AI's {@code Usage#getCacheWriteInputTokens}), zero when none or not reported
 */
public record TokenUsage(
    int promptTokens,
    int completionTokens,
    int totalTokens,
    int cachedReadTokens,
    int cacheWriteTokens) {

  private static final BigDecimal TOKENS_PER_KILOTOKEN = new BigDecimal("1000");

  /**
   * Sentinel for an inference whose provider reported no usage at all. {@code -1} rather than
   * {@code 0} because the two are different facts: nothing measured, versus nothing consumed.
   */
  private static final int UNREPORTED = -1;

  private static final TokenUsage NONE = new TokenUsage(UNREPORTED, UNREPORTED, UNREPORTED, 0, 0);

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public TokenUsage {
    requireCountable(promptTokens, "promptTokens");
    requireCountable(completionTokens, "completionTokens");
    requireCountable(totalTokens, "totalTokens");
    if (cachedReadTokens < 0 || cacheWriteTokens < 0) {
      throw new IllegalArgumentException("cached token counts must be >= 0");
    }
  }

  /** A usage with no cached tokens: what a provider without a prompt cache reports. */
  public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    this(promptTokens, completionTokens, totalTokens, 0, 0);
  }

  private static void requireCountable(int tokens, String field) {
    if (tokens < UNREPORTED) {
      throw new IllegalArgumentException(field + " must be >= 0, or -1 when unreported");
    }
  }

  /**
   * The usage of an inference whose provider reported none.
   *
   * @return the unreported sentinel
   */
  public static TokenUsage none() {
    return NONE;
  }

  /**
   * Records what a provider reported, defaulting each missing half to zero.
   *
   * @param promptTokens tokens in the prompt, or {@code null} if the provider omitted it
   * @param completionTokens tokens generated, or {@code null} if the provider omitted it
   * @param totalTokens the provider's total, or {@code null} to sum the two halves
   * @return the reported usage
   */
  public static TokenUsage reported(
      Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    return reported(promptTokens, completionTokens, totalTokens, null, null);
  }

  /**
   * Records what a provider reported, prompt-cache counters included.
   *
   * @param cachedReadTokens tokens served from the provider's prompt cache, or {@code null}
   * @param cacheWriteTokens tokens written to the provider's prompt cache, or {@code null}
   * @see #reported(Integer, Integer, Integer)
   */
  public static TokenUsage reported(
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      Long cachedReadTokens,
      Long cacheWriteTokens) {
    int prompt = promptTokens == null ? 0 : promptTokens;
    int completion = completionTokens == null ? 0 : completionTokens;
    return new TokenUsage(
        prompt,
        completion,
        totalTokens == null ? prompt + completion : totalTokens,
        clamp(cachedReadTokens),
        clamp(cacheWriteTokens));
  }

  private static int clamp(Long tokens) {
    return tokens == null ? 0 : (int) Math.min(Math.max(tokens, 0L), Integer.MAX_VALUE);
  }

  /**
   * This usage and another one, added up (a turn with several model rounds).
   *
   * @param other the other usage
   * @return the sum; an unreported side contributes nothing
   */
  public TokenUsage plus(TokenUsage other) {
    if (other == null || !other.reported()) {
      return this;
    }
    if (!reported()) {
      return other;
    }
    return new TokenUsage(
        promptTokens + other.promptTokens,
        completionTokens + other.completionTokens,
        totalTokens + other.totalTokens,
        cachedReadTokens + other.cachedReadTokens,
        cacheWriteTokens + other.cacheWriteTokens);
  }

  /**
   * Whether the provider reported usage at all.
   *
   * @return {@code false} when nothing was reported — an unmeasured turn, which releases its hold
   *     rather than being billed at an estimate
   */
  public boolean reported() {
    return promptTokens != UNREPORTED;
  }

  /**
   * This turn's consumption in the unit chat is priced in.
   *
   * @return total tokens as kilotokens, or zero when nothing was reported
   */
  public BigDecimal kilotokens() {
    if (!reported()) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(totalTokens).divide(TOKENS_PER_KILOTOKEN, MathContext.DECIMAL64);
  }

  /**
   * The share of this turn's input served from the provider's cache, in the unit chat is priced in
   * (ADR-094 slice 5, #173). These tokens are already inside {@link #kilotokens()}: the billed
   * quantity does not change, this is the part a cheaper cached-read price would apply to.
   *
   * @return cache-read tokens as kilotokens, or zero when nothing was reported
   */
  public BigDecimal cachedReadKilotokens() {
    return reported() ? perKilotoken(cachedReadTokens) : BigDecimal.ZERO;
  }

  /**
   * The input written to the provider's cache this turn, as kilotokens (a cache write is billed
   * above the plain input price by some providers).
   *
   * @return cache-write tokens as kilotokens, or zero when nothing was reported
   */
  public BigDecimal cacheWriteKilotokens() {
    return reported() ? perKilotoken(cacheWriteTokens) : BigDecimal.ZERO;
  }

  private static BigDecimal perKilotoken(int tokens) {
    return BigDecimal.valueOf(tokens).divide(TOKENS_PER_KILOTOKEN, MathContext.DECIMAL64);
  }
}
