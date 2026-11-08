package com.krizaka.sovkora.crypto.domain.port;

import java.io.Serial;

/**
 * A data key could not be unwrapped: the master key is unknown here, or the blob did not
 * authenticate under it.
 *
 * <p>Both are failures to open. Neither falls back to reading the file as plaintext — a cipher that
 * degrades to no cipher when the key is missing protects nothing.
 */
public class MasterKeyUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param message what could not be opened, and why — never key material
   * @param cause the underlying failure, or {@code null}
   */
  public MasterKeyUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
