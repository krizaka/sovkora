package com.krizaka.sovkora.core.domain.model.checks;

import java.io.Serial;

/**
 * Thrown by a connector that cannot call its provider because the endpoint or the key is missing or
 * unusable. The service turns it into {@code REFUSED / NOT_CONFIGURED}: the check fails closed. The
 * message never carries the key.
 */
public class ProviderNotConfiguredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param message what is missing, never a secret
   */
  public ProviderNotConfiguredException(String message) {
    super(message);
  }
}
