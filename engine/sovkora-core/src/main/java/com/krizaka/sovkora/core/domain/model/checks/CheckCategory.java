package com.krizaka.sovkora.core.domain.model.checks;

/**
 * What kind of record a court-records match found (sovkora-engine#84), so that a manager's cut-offs
 * can tell a TAL non-payment file from another file. A closed list: never free text, never the
 * record's content.
 */
public enum CheckCategory {
  /** A Tribunal administratif du logement file for non-payment of rent. */
  TAL_NON_PAYMENT,
  /** Another Tribunal administratif du logement file. */
  TAL_OTHER,
  /** A criminal or penal record (plumitif). */
  CRIMINAL,
  /** A civil record (plumitif). */
  CIVIL,
  /** No category: no match, or a purpose that has none (credit, identity). */
  NONE
}
