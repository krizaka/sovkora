package com.krizaka.sovkora.engine.interceptors.governance;

/** What a scope's cap does once the month's provider spend reaches it. Stored as a row. */
public enum SpendCapAction {
  /** Serve the turn on the cheaper chat model the cap row names. */
  DOWNGRADE,
  /** Stop the turn with a typed refusal. */
  REFUSE
}
