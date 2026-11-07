package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Objects;

/**
 * The outcome of one external check. It carries no record content: a match is an outcome and a
 * closed {@link CheckCategory}, not data.
 *
 * @param outcome what the check answered
 * @param refusal why it was refused, present exactly when the outcome is {@link
 *     CheckOutcome#REFUSED}
 * @param category the kind of record a match found; {@link CheckCategory#NONE} for anything but a
 *     match
 * @param providerRef an opaque reference of the case at the provider, or {@code null}; never built
 *     from the subject's name, birth date or address
 */
public record CheckResult(
    CheckOutcome outcome, CheckRefusal refusal, CheckCategory category, String providerRef) {

  /** Validates that a refusal comes with REFUSED, and a category only with a match. */
  public CheckResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(category, "category must not be null");
    if ((outcome == CheckOutcome.REFUSED) != (refusal != null)) {
      throw new IllegalArgumentException(
          "a refusal comes with REFUSED, and REFUSED with a refusal");
    }
    if (outcome == CheckOutcome.PENDING && (providerRef == null || providerRef.isBlank())) {
      throw new IllegalArgumentException("a pending check carries the provider's reference");
    }
    if (outcome != CheckOutcome.MATCH && category != CheckCategory.NONE) {
      throw new IllegalArgumentException("a category comes only with a match");
    }
  }

  /**
   * An answer from the provider, with no category.
   *
   * @param outcome MATCH, NO_MATCH, PROVIDER_ERROR or TIMEOUT
   * @param providerRef the provider's reference, or {@code null}
   * @return the result
   */
  public static CheckResult answered(CheckOutcome outcome, String providerRef) {
    return answered(outcome, CheckCategory.NONE, providerRef);
  }

  /**
   * An answer from the provider.
   *
   * @param outcome MATCH, NO_MATCH, PROVIDER_ERROR or TIMEOUT
   * @param category the kind of record a match found, {@link CheckCategory#NONE} otherwise
   * @param providerRef the provider's reference, or {@code null}
   * @return the result
   */
  public static CheckResult answered(
      CheckOutcome outcome, CheckCategory category, String providerRef) {
    if (outcome == CheckOutcome.REFUSED) {
      throw new IllegalArgumentException("use refused() for a refusal");
    }
    return new CheckResult(outcome, null, category, providerRef);
  }

  /**
   * A call refused before any provider call.
   *
   * @param refusal why
   * @return the result
   */
  public static CheckResult refused(CheckRefusal refusal) {
    return new CheckResult(
        CheckOutcome.REFUSED, Objects.requireNonNull(refusal), CheckCategory.NONE, null);
  }

  /**
   * Whether the provider answered (a match or no match), the only case that is charged.
   *
   * @return true on MATCH or NO_MATCH
   */
  public boolean isAnswered() {
    return outcome == CheckOutcome.MATCH || outcome == CheckOutcome.NO_MATCH;
  }

  /**
   * Whether the call is charged: the provider answered, or accepted the search to answer later.
   *
   * @return true on MATCH, NO_MATCH or PENDING
   */
  public boolean isCharged() {
    return isAnswered() || outcome == CheckOutcome.PENDING;
  }
}
