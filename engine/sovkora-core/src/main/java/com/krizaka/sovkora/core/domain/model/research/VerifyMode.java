package com.krizaka.sovkora.core.domain.model.research;

/** What the verifier does with an unverified claim ({@code research.verify.mode}). */
public enum VerifyMode {
  /** Keep it, marked {@link ClaimStatus#TO_VALIDATE} with its reason. The default. */
  MARK,
  /** Drop it; the answer keeps only verified claims. */
  STRICT
}
