package com.krizaka.sovkora.core.domain.model.research;

/** Why a claim is {@link ClaimStatus#TO_VALIDATE} (ADR-087 §3). */
public enum ClaimReason {
  /** The claim cites nothing. */
  NO_SOURCE,
  /** No cited URL and hash name a document this answer read. */
  URL_UNRESOLVED,
  /** No quote of the claim appears in the document it names. */
  QUOTE_NOT_FOUND,
  /**
   * The quote is genuine, but a number, date or reference the claim states is neither in the quote
   * nor in the document's title or section heading.
   */
  UNSUPPORTED,
  /** The source the claim needs is disabled. */
  SOURCE_DISABLED,
  /** A URL, a repository or a statute in the answer that no verified claim cites. */
  UNLISTED_REFERENCE,
  /** The research budget of the answer was spent before this could be checked. */
  BUDGET
}
