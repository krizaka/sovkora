package com.krizaka.sovkora.core.domain.model.control;

/**
 * The metadata keys under which a control verdict travels in a response, for the UI
 * (sovkora-engine#125). The value of {@link #VERDICT} is the name of a {@link ControlState}; the
 * value of {@link #BADGE} is a {@link ControlBadge#value()}.
 */
public enum ControlMetadata {

  /** The verdict state of the answer. */
  VERDICT("control.verdict"),

  /** The badge a degraded verification must show. */
  BADGE("control.badge");

  private final String key;

  ControlMetadata(String key) {
    this.key = key;
  }

  /**
   * @return the metadata key
   */
  public String key() {
    return key;
  }
}
