package com.krizaka.sovkora.automationservice.domain.port;

import java.util.UUID;

/** Starts the Studio run of a recorded run, off the caller's thread. */
public interface RunDispatcher {

  /**
   * Hands a recorded run over to be started. Never blocks and never throws: a run that is not
   * started now is started by the next sweep.
   *
   * @param runId the recorded run
   */
  void submit(UUID runId);
}
