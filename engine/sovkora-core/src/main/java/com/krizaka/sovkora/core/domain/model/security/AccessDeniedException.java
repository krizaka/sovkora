package com.krizaka.sovkora.core.domain.model.security;

import java.io.Serial;

/**
 * A caller asked for something its actor may not use (ADR-091). The message names the resource and
 * the reason, never content, so it is safe to log and to return.
 */
public class AccessDeniedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Why the caller was refused. */
  public enum Reason {
    /** The call acts for nobody: no verified identity. */
    NO_ACTOR,
    /** The actor holds none of the roles the resource requires. */
    MISSING_ROLE,
    /** The model called a tool this turn did not offer (ADR-093 §4). */
    NOT_OFFERED
  }

  private final Reason reason;

  /**
   * @param resource what was refused, e.g. a tool name
   * @param reason why
   */
  public AccessDeniedException(String resource, Reason reason) {
    super("Access to " + resource + " is refused: " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
