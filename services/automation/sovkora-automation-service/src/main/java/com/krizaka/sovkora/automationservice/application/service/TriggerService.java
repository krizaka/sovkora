package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyTriggersException;
import com.krizaka.sovkora.automationservice.domain.exception.TriggerNotFoundException;
import com.krizaka.sovkora.automationservice.domain.model.CreatedTrigger;
import com.krizaka.sovkora.automationservice.domain.model.NewTrigger;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.model.Trigger;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import com.krizaka.sovkora.automationservice.domain.model.WebhookSecret;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import com.krizaka.sovkora.automationservice.domain.port.SecretSealer;
import com.krizaka.sovkora.automationservice.domain.port.TriggerRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The triggers of the owner's automations. Every call proves the automation is the owner's first,
 * so a trigger of someone else is not found, never forbidden. A webhook secret is generated here,
 * sealed before it is stored, handed back once and never read back (ADR-084 §1).
 */
@Service
public class TriggerService {

  private final AutomationRepository automations;
  private final TriggerRepository triggers;
  private final SecretSealer sealer;
  private final AutomationSettingsService settings;
  private final Clock clock;

  public TriggerService(
      AutomationRepository automations,
      TriggerRepository triggers,
      SecretSealer sealer,
      AutomationSettingsService settings,
      Clock clock) {
    this.automations = Objects.requireNonNull(automations, "AutomationRepository cannot be null");
    this.triggers = Objects.requireNonNull(triggers, "TriggerRepository cannot be null");
    this.sealer = Objects.requireNonNull(sealer, "SecretSealer cannot be null");
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsService cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  /**
   * Adds a trigger.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param draft what to add
   * @return the trigger, and for a webhook its secret, shown once
   * @throws AutomationNotFoundException when the owner has no such automation
   * @throws TooManyTriggersException when the automation holds as many as allowed
   */
  public CreatedTrigger create(String ownerId, UUID automationId, NewTrigger draft) {
    requireAutomation(ownerId, automationId);
    int limit = settings.value(SettingKey.MAX_TRIGGERS_PER_AUTOMATION);
    if (triggers.count(automationId) >= limit) {
      throw new TooManyTriggersException(limit);
    }
    UUID id = UUID.randomUUID();
    if (draft.kind() == TriggerKind.WEBHOOK) {
      WebhookSecret secret = WebhookSecret.generate();
      Trigger stored =
          triggers.createWebhook(id, automationId, sealer.seal(id, secret.reveal()), secret.hint());
      return new CreatedTrigger(stored, secret.reveal());
    }
    return new CreatedTrigger(
        triggers.createSchedule(
            id,
            automationId,
            draft.schedule(),
            draft.catchUp(),
            draft.schedule().nextAfter(clock.instant())),
        null);
  }

  /**
   * The triggers of an automation.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @return the triggers, oldest first
   * @throws AutomationNotFoundException when the owner has no such automation
   */
  public List<Trigger> list(String ownerId, UUID automationId) {
    requireAutomation(ownerId, automationId);
    return triggers.list(ownerId, automationId);
  }

  /**
   * Enables or disables a trigger.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @param enabled the new state
   * @return the trigger
   * @throws TriggerNotFoundException when the owner has no such trigger
   */
  public Trigger setEnabled(String ownerId, UUID automationId, UUID triggerId, boolean enabled) {
    Trigger current = get(ownerId, automationId, triggerId);
    var next =
        enabled && current.schedule() != null
            ? current.schedule().nextAfter(clock.instant())
            : null;
    return triggers
        .setEnabled(ownerId, automationId, triggerId, enabled, next)
        .orElseThrow(() -> new TriggerNotFoundException(triggerId));
  }

  /**
   * Removes a trigger.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @throws TriggerNotFoundException when the owner has no such trigger
   */
  public void delete(String ownerId, UUID automationId, UUID triggerId) {
    if (!triggers.delete(ownerId, automationId, triggerId)) {
      throw new TriggerNotFoundException(triggerId);
    }
  }

  /**
   * Issues a new secret for a webhook trigger. The previous one keeps verifying for the grace
   * period, so the sender can switch without losing a call.
   *
   * @param ownerId the owner
   * @param automationId the automation
   * @param triggerId the trigger
   * @return the trigger, and its new secret, shown once
   * @throws TriggerNotFoundException when the owner has no such trigger
   * @throws IllegalArgumentException when the trigger is not a webhook
   */
  public CreatedTrigger rotate(String ownerId, UUID automationId, UUID triggerId) {
    Trigger current = get(ownerId, automationId, triggerId);
    if (current.kind() != TriggerKind.WEBHOOK) {
      throw new IllegalArgumentException("only a webhook trigger has a secret");
    }
    WebhookSecret secret = WebhookSecret.generate();
    Duration grace = Duration.ofHours(settings.value(SettingKey.WEBHOOK_SECRET_GRACE_HOURS));
    Trigger rotated =
        triggers
            .rotateSecret(
                ownerId,
                automationId,
                triggerId,
                sealer.seal(triggerId, secret.reveal()),
                secret.hint(),
                grace)
            .orElseThrow(() -> new TriggerNotFoundException(triggerId));
    return new CreatedTrigger(rotated, secret.reveal());
  }

  private Trigger get(String ownerId, UUID automationId, UUID triggerId) {
    return triggers
        .find(ownerId, automationId, triggerId)
        .orElseThrow(() -> new TriggerNotFoundException(triggerId));
  }

  private void requireAutomation(String ownerId, UUID automationId) {
    automations
        .find(ownerId, automationId)
        .orElseThrow(() -> new AutomationNotFoundException(automationId));
  }
}
