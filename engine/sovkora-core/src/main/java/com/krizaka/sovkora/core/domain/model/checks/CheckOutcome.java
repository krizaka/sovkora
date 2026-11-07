package com.krizaka.sovkora.core.domain.model.checks;

/** What an external check answered. {@link #REFUSED} means the provider was never reached. */
public enum CheckOutcome {
  /** The subject matches the provider's record. */
  MATCH,
  /** The provider has no record for the subject. */
  NO_MATCH,
  /** The provider answered an error. Its message is never copied into the result or the audit. */
  PROVIDER_ERROR,
  /** The provider did not answer in time. */
  TIMEOUT,
  /**
   * The provider accepted the search and will answer later (webhook or polling): the result carries
   * its reference. Charged once, at submission.
   */
  PENDING,
  /** The call was refused before any provider call: see {@link CheckRefusal}. */
  REFUSED
}
