package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;
import java.util.UUID;

/** The caller owns no automation with this id (a row of someone else is indistinguishable). */
public class AutomationNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param id the id asked for
   */
  public AutomationNotFoundException(UUID id) {
    super("no automation " + id);
  }
}
