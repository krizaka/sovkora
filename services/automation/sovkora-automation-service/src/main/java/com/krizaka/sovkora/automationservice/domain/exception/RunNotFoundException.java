package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;
import java.util.UUID;

/** The caller owns no such run. */
public class RunNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param id the id asked for
   */
  public RunNotFoundException(UUID id) {
    super("no run " + id);
  }
}
