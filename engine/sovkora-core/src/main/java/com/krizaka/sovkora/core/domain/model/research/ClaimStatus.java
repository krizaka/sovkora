package com.krizaka.sovkora.core.domain.model.research;

/** Whether a claim of a grounded answer was checked against what was read (ADR-087 §3). */
public enum ClaimStatus {
  /** At least one of its quotes was found in the document it cites. */
  VERIFIED,
  /** Not verified: kept and marked (mode MARK) for a person to check, never asserted. */
  TO_VALIDATE
}
