package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The triggers of the owner's automations: every method carries the owner, so a trigger never leaks
 * across.
 */
public interface TriggerRepository {

  /**
   * How many triggers an automation holds.
   *
   * @param automationId the automation
   * @return the count
   */
  int count(UUID automationId);

  /**
   * Stores a webhook trigger with its sealed secret.
   *
   * @param id the trigger id, chosen by the caller so the secret can be sealed against it
   * @param automationId the automation
   * @param secretSealed the sealed secret
   * @param secretHint the last characters of the secret
   * @return the stored trigger
   */
  Trigger createWebhook(UUID id, UUID automationId, String secretSealed, String secretHint);

  /**
   * Stores a schedule trigger.
   *
   * @param id the trigger id
   * @param automationId the automation
   * @param schedule the slot rule
   * @param catchUp the missed-slot policy
   * @param nextFireAt the first slot
   * @return the stored trigger
   */
  Trigger createSchedule(
      UUID id, UUID automationId, ScheduleSpec schedule, CatchUp catchUp, Instant nextFireAt);

  /**
   * The triggers of one automation of the owner, oldest first.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @return the triggers, empty when the owner has no such automation
   */
  List<Trigger> list(String ownerId, UUID automationId);

  /**
   * One trigger of the owner.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @return the trigger, empty when the owner has none
   */
  Optional<Trigger> find(String ownerId, UUID automationId, UUID triggerId);

  /**
   * Enables or disables a trigger.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @param enabled the new state
   * @param nextFireAt the next slot of a schedule being enabled, null otherwise
   * @return the trigger, empty when the owner has none
   */
  Optional<Trigger> setEnabled(
      String ownerId, UUID automationId, UUID triggerId, boolean enabled, Instant nextFireAt);

  /**
   * Removes a trigger. Its past runs stay in the history.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @return whether something was removed
   */
  boolean delete(String ownerId, UUID automationId, UUID triggerId);

  /**
   * Replaces a webhook trigger's secret; the previous one keeps verifying for the grace.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @param secretSealed the new sealed secret
   * @param secretHint the last characters of the new secret
   * @param grace how long the previous secret keeps verifying
   * @return the trigger, empty when the owner has no such webhook trigger
   */
  Optional<Trigger> rotateSecret(
      String ownerId,
      UUID automationId,
      UUID triggerId,
      String secretSealed,
      String secretHint,
      Duration grace);
}
