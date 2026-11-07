package com.krizaka.sovkora.jobs.api.api.domain.exception;

import java.io.Serial;

/**
 * A capability could not be registered or removed.
 *
 * <p>Thrown rather than swallowed because the caller is an installer inside a transaction: a
 * capability write that failed quietly would leave a pack whose blueprints name keys that dispatch
 * nowhere, and the failure would first be seen by a user paying for a run that cannot start.
 */
public class CapabilityRegistrationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what could not be written, and where
   * @param cause the transport or persistence failure underneath
   */
  public CapabilityRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates the exception with no underlying cause.
   *
   * @param message what could not be written, and where
   */
  public CapabilityRegistrationException(String message) {
    super(message);
  }
}
