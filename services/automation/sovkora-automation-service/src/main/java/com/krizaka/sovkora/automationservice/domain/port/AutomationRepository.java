package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.Automation;
import com.krizaka.sovkora.automationservice.domain.model.AutomationChange;
import com.krizaka.sovkora.automationservice.domain.model.NewAutomation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The automations of the service; every method carries the owner, so a row never leaks across. */
public interface AutomationRepository {

  /**
   * How many automations the owner holds.
   *
   * @param ownerId the owner
   * @return the count
   */
  int count(String ownerId);

  /**
   * Stores a new automation.
   *
   * @param ownerId the owner
   * @param draft what to store
   * @return the stored automation
   */
  Automation create(String ownerId, NewAutomation draft);

  /**
   * The owner's automations, oldest first.
   *
   * @param ownerId the owner
   * @return the automations
   */
  List<Automation> list(String ownerId);

  /**
   * One automation of the owner.
   *
   * @param ownerId the owner
   * @param id the automation
   * @return the automation, empty when the owner has none with this id
   */
  Optional<Automation> find(String ownerId, UUID id);

  /**
   * Applies a change.
   *
   * @param ownerId the owner
   * @param id the automation
   * @param change what to change
   * @return the changed automation, empty when the owner has none with this id
   */
  Optional<Automation> update(String ownerId, UUID id, AutomationChange change);

  /**
   * Removes an automation with its triggers and runs.
   *
   * @param ownerId the owner
   * @param id the automation
   * @return whether something was removed
   */
  boolean delete(String ownerId, UUID id);
}
