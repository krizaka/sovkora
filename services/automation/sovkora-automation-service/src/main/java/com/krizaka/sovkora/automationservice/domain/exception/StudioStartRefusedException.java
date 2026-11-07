package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;

/**
 * The engine did not accept the Studio run; the code and words are its own, kept for the history.
 */
public class StudioStartRefusedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * @param code a stable code, e.g. {@code studio_refused_402}
   * @param message the refusal in a few words, never a body that could carry a secret
   */
  public StudioStartRefusedException(String code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * The stable code.
   *
   * @return the code
   */
  public String code() {
    return code;
  }
}
