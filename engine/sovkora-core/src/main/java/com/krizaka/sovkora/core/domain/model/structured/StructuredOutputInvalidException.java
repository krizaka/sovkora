package com.krizaka.sovkora.core.domain.model.structured;

import java.io.Serial;

/**
 * The model never produced an answer of the requested shape within the attempts allowed (ADR-094
 * §4.6, #172). It is the typed error a caller sees (it feeds the control verdict), never a silent
 * default. It stays an {@link IllegalStateException}, the type the port documented before.
 */
public class StructuredOutputInvalidException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  private final String type;
  private final int attempts;

  /**
   * Creates the error.
   *
   * @param type the simple name of the requested shape
   * @param attempts how many answers were asked for
   * @param reason what was wrong with the last answer
   * @param cause the underlying failure, may be {@code null}
   */
  public StructuredOutputInvalidException(
      String type, int attempts, String reason, Throwable cause) {
    super(
        "The model's answer is not a " + type + " after " + attempts + " attempt(s): " + reason,
        cause);
    this.type = type;
    this.attempts = attempts;
  }

  /**
   * @return the simple name of the requested shape
   */
  public String type() {
    return type;
  }

  /**
   * @return how many answers were asked for
   */
  public int attempts() {
    return attempts;
  }
}
