package com.krizaka.sovkora.core.domain.model;

/**
 * Signals model unavailability, the only invocation failure that permits trying the next declared
 * candidate. Billing, safety, validation and other failures must not use this type.
 */
public class StepModelUnavailableException extends RuntimeException {

  /**
   * Creates an unavailability failure.
   *
   * @param message failure description
   */
  public StepModelUnavailableException(String message) {
    super(message);
  }

  /**
   * Creates an unavailability failure retaining its cause.
   *
   * @param message failure description
   * @param cause underlying failure, or null when no candidate was attempted
   */
  public StepModelUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
