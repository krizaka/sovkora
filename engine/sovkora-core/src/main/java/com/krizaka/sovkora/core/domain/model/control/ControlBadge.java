package com.krizaka.sovkora.core.domain.model.control;

/** The badge a response carries (metadata key {@link ControlMetadata#BADGE}). */
public enum ControlBadge {

  /** « Vérification sémantique indisponible »: served on the deterministic tiers alone. */
  VERIFICATION_UNAVAILABLE("verification_unavailable");

  private final String value;

  ControlBadge(String value) {
    this.value = value;
  }

  /**
   * @return the value stored in the metadata
   */
  public String value() {
    return value;
  }
}
