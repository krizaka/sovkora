package com.krizaka.sovkora.automationservice.domain.exception;

import java.io.Serial;

/** The owner already holds as many automations as the rule set allows. */
public class TooManyAutomationsException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param limit how many one owner may hold
   */
  public TooManyAutomationsException(int limit) {
    super("an owner may hold at most " + limit + " automations");
  }
}
