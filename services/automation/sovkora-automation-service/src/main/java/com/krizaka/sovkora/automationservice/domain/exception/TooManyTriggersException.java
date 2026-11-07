package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;

/** The automation already holds as many triggers as the rule set allows. */
public class TooManyTriggersException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param limit how many one automation may hold
   */
  public TooManyTriggersException(int limit) {
    super("an automation may hold at most " + limit + " triggers");
  }
}
