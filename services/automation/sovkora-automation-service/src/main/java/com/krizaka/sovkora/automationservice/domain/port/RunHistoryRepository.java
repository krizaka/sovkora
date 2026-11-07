package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.AutomationRun;
import com.krizaka.sovkora.automationservice.domain.model.RunDetail;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads the runs of an owner's automations, newest first. */
public interface RunHistoryRepository {

  /**
   * The owner's runs, newest first.
   *
   * @param ownerId the owner
   * @param automationId one automation, or null for all
   * @param limit the most to return
   * @return the runs
   */
  List<AutomationRun> list(String ownerId, UUID automationId, int limit);

  /**
   * One run of the owner.
   *
   * @param ownerId the owner
   * @param runId the run
   * @return the run, empty when the owner has none
   */
  Optional<RunDetail> find(String ownerId, UUID runId);
}
