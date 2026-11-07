package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyAutomationsException;
import com.krizaka.sovkora.automationservice.domain.model.Automation;
import com.krizaka.sovkora.automationservice.domain.model.AutomationChange;
import com.krizaka.sovkora.automationservice.domain.model.NewAutomation;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * The owner's automations. Every call is scoped by the owner id: an automation of someone else is
 * not found, never forbidden, so the API is no oracle for ids (ADR-035).
 */
@Service
public class AutomationService {

  private final AutomationRepository automations;
  private final AutomationSettingsService settings;
  private final ObjectMapper json;

  public AutomationService(
      AutomationRepository automations, AutomationSettingsService settings, ObjectMapper json) {
    this.automations = Objects.requireNonNull(automations, "AutomationRepository cannot be null");
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsService cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  /**
   * Creates an automation.
   *
   * @param ownerId the owner
   * @param draft what to create
   * @return the stored automation
   * @throws TooManyAutomationsException when the owner holds as many as the rule set allows
   * @throws IllegalArgumentException when the inputs are larger than the rule set allows
   */
  public Automation create(String ownerId, NewAutomation draft) {
    requireInputsWithinLimit(draft.inputs());
    int limit = settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER);
    if (automations.count(ownerId) >= limit) {
      throw new TooManyAutomationsException(limit);
    }
    return automations.create(ownerId, draft);
  }

  /**
   * The owner's automations.
   *
   * @param ownerId the owner
   * @return the automations, oldest first
   */
  public List<Automation> list(String ownerId) {
    return automations.list(ownerId);
  }

  /**
   * One automation.
   *
   * @param ownerId the owner
   * @param id the automation
   * @return the automation
   * @throws AutomationNotFoundException when the owner has none with this id
   */
  public Automation get(String ownerId, UUID id) {
    return automations.find(ownerId, id).orElseThrow(() -> new AutomationNotFoundException(id));
  }

  /**
   * Changes an automation.
   *
   * @param ownerId the owner
   * @param id the automation
   * @param change what to change
   * @return the changed automation
   * @throws AutomationNotFoundException when the owner has none with this id
   */
  public Automation update(String ownerId, UUID id, AutomationChange change) {
    if (change.inputs() != null) {
      requireInputsWithinLimit(change.inputs());
    }
    if (change.isEmpty()) {
      return get(ownerId, id);
    }
    return automations
        .update(ownerId, id, change)
        .orElseThrow(() -> new AutomationNotFoundException(id));
  }

  /**
   * Removes an automation with its triggers and history.
   *
   * @param ownerId the owner
   * @param id the automation
   * @throws AutomationNotFoundException when the owner has none with this id
   */
  public void delete(String ownerId, UUID id) {
    if (!automations.delete(ownerId, id)) {
      throw new AutomationNotFoundException(id);
    }
  }

  private void requireInputsWithinLimit(Map<String, Object> inputs) {
    int limit = settings.value(SettingKey.MAX_INPUTS_BYTES);
    if (json.writeValueAsBytes(inputs).length > limit) {
      throw new IllegalArgumentException("the inputs are at most " + limit + " bytes");
    }
  }
}
