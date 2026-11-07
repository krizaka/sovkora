package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;
import java.util.UUID;

/** The caller owns no such trigger (a trigger of someone else is indistinguishable). */
public class TriggerNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param id the id asked for
   */
  public TriggerNotFoundException(UUID id) {
    super("no trigger " + id);
  }
}
