package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Starts and reads the Studio runs that automations are made of, on behalf of their owners. */
public interface StudioRunStarter {

  /**
   * Starts a run of a Studio as the owner. A retry with the same key starts nothing new.
   *
   * @param ownerId the actor the run belongs to
   * @param studioKey the Studio
   * @param inputs the answers to its form
   * @param idempotencyKey makes a retry safe
   * @return the Studio run's id
   * @throws com.krizaka.sovkora.automationservice.domain.exception.StudioStartRefusedException when
   *     the engine refuses or cannot be reached
   */
  UUID start(String ownerId, String studioKey, Map<String, Object> inputs, String idempotencyKey);

  /**
   * Reads where a Studio run is.
   *
   * @param ownerId the actor the run belongs to
   * @param studioRunId the Studio run
   * @return its status when it has ended, empty while it goes on or cannot be read
   */
  Optional<RunStatus> endedStatus(String ownerId, UUID studioRunId);
}
