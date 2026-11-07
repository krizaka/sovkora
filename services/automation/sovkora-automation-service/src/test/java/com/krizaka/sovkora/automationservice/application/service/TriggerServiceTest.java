package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyTriggersException;
import com.krizaka.sovkora.automationservice.domain.exception.TriggerNotFoundException;
import com.krizaka.sovkora.automationservice.domain.model.Automation;
import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.CreatedTrigger;
import com.krizaka.sovkora.automationservice.domain.model.NewTrigger;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.model.Trigger;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import com.krizaka.sovkora.automationservice.domain.port.SecretSealer;
import com.krizaka.sovkora.automationservice.domain.port.TriggerRepository;
import com.krizaka.sovkora.automationservice.support.FixedClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TriggerServiceTest {

  private static final String OWNER = "owner-1";
  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  private final AutomationRepository automations = mock(AutomationRepository.class);
  private final TriggerRepository triggers = mock(TriggerRepository.class);
  private final SecretSealer sealer = mock(SecretSealer.class);
  private final AutomationSettingsService settings = mock(AutomationSettingsService.class);
  private final UUID automationId = UUID.randomUUID();
  private TriggerService service;

  @BeforeEach
  void setUp() {
    service = new TriggerService(automations, triggers, sealer, settings, new FixedClock(NOW));
    for (SettingKey key : SettingKey.values()) {
      when(settings.value(key)).thenReturn(key.defaultValue());
    }
    when(automations.find(OWNER, automationId))
        .thenReturn(
            Optional.of(new Automation(automationId, OWNER, "n", "k", Map.of(), true, NOW, NOW)));
    when(sealer.seal(any(), anyString())).thenAnswer(call -> "sealed(" + call.getArgument(1) + ")");
  }

  private static Trigger webhook(UUID id, UUID automation) {
    return new Trigger(
        id, automation, TriggerKind.WEBHOOK, true, "abcd", null, CatchUp.ONCE, null, null, NOW);
  }

  @Test
  @DisplayName(
      "a webhook trigger is stored sealed, never in clear, and its secret is returned once")
  void createsWebhook() {
    when(triggers.createWebhook(any(), eq(automationId), anyString(), anyString()))
        .thenAnswer(call -> webhook(call.getArgument(0), automationId));

    CreatedTrigger created =
        service.create(OWNER, automationId, new NewTrigger(TriggerKind.WEBHOOK, null, null));

    assertThat(created.secret()).startsWith("whsec_");
    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> hint = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(triggers)
        .createWebhook(id.capture(), eq(automationId), stored.capture(), hint.capture());
    assertThat(stored.getValue()).isEqualTo("sealed(" + created.secret() + ")");
    assertThat(created.secret()).endsWith(hint.getValue());
    verify(sealer).seal(id.getValue(), created.secret());
    assertThat(created.toString()).doesNotContain(created.secret());
  }

  @Test
  @DisplayName("a schedule trigger starts at its next slot after now")
  void createsSchedule() {
    ScheduleSpec slot = new ScheduleSpec(LocalTime.of(13, 0), ZoneOffset.UTC, Set.of());
    when(triggers.createSchedule(any(), eq(automationId), eq(slot), eq(CatchUp.SKIP), any()))
        .thenAnswer(
            call ->
                new Trigger(
                    call.getArgument(0),
                    automationId,
                    TriggerKind.SCHEDULE,
                    true,
                    null,
                    slot,
                    CatchUp.SKIP,
                    call.getArgument(4),
                    null,
                    NOW));

    CreatedTrigger created =
        service.create(
            OWNER, automationId, new NewTrigger(TriggerKind.SCHEDULE, slot, CatchUp.SKIP));

    assertThat(created.secret()).isNull();
    assertThat(created.trigger().nextFireAt()).isEqualTo(Instant.parse("2026-10-20T13:00:00Z"));
    verify(sealer, never()).seal(any(), anyString());
  }

  @Test
  @DisplayName("a trigger cannot be added to an automation the owner does not have")
  void ownership() {
    UUID foreign = UUID.randomUUID();
    when(automations.find(OWNER, foreign)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.create(OWNER, foreign, new NewTrigger(TriggerKind.WEBHOOK, null, null)))
        .isInstanceOf(AutomationNotFoundException.class);
    assertThatThrownBy(() -> service.list(OWNER, foreign))
        .isInstanceOf(AutomationNotFoundException.class);
    verify(triggers, never()).createWebhook(any(), any(), any(), any());
  }

  @Test
  @DisplayName("the number of triggers of an automation is bounded by the rule set")
  void quota() {
    when(triggers.count(automationId))
        .thenReturn(SettingKey.MAX_TRIGGERS_PER_AUTOMATION.defaultValue());
    assertThatThrownBy(
            () ->
                service.create(
                    OWNER, automationId, new NewTrigger(TriggerKind.WEBHOOK, null, null)))
        .isInstanceOf(TooManyTriggersException.class);
  }

  @Test
  @DisplayName(
      "rotation seals a new secret and keeps the old one for the grace period of the rule set")
  void rotates() {
    UUID triggerId = UUID.randomUUID();
    when(triggers.find(OWNER, automationId, triggerId))
        .thenReturn(Optional.of(webhook(triggerId, automationId)));
    when(triggers.rotateSecret(
            eq(OWNER), eq(automationId), eq(triggerId), anyString(), anyString(), any()))
        .thenReturn(Optional.of(webhook(triggerId, automationId)));

    CreatedTrigger rotated = service.rotate(OWNER, automationId, triggerId);

    assertThat(rotated.secret()).startsWith("whsec_");
    verify(triggers)
        .rotateSecret(
            eq(OWNER),
            eq(automationId),
            eq(triggerId),
            eq("sealed(" + rotated.secret() + ")"),
            anyString(),
            eq(Duration.ofHours(SettingKey.WEBHOOK_SECRET_GRACE_HOURS.defaultValue())));
    verify(sealer).seal(triggerId, rotated.secret());
  }

  @Test
  @DisplayName("only a webhook has a secret to rotate")
  void rotateSchedule() {
    UUID triggerId = UUID.randomUUID();
    ScheduleSpec slot = new ScheduleSpec(LocalTime.NOON, ZoneOffset.UTC, Set.of());
    when(triggers.find(OWNER, automationId, triggerId))
        .thenReturn(
            Optional.of(
                new Trigger(
                    triggerId,
                    automationId,
                    TriggerKind.SCHEDULE,
                    true,
                    null,
                    slot,
                    CatchUp.ONCE,
                    NOW,
                    null,
                    NOW)));
    assertThatThrownBy(() -> service.rotate(OWNER, automationId, triggerId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("enabling a schedule computes its next slot, disabling clears it")
  void enableDisable() {
    UUID triggerId = UUID.randomUUID();
    ScheduleSpec slot = new ScheduleSpec(LocalTime.of(13, 0), ZoneOffset.UTC, Set.of());
    Trigger schedule =
        new Trigger(
            triggerId,
            automationId,
            TriggerKind.SCHEDULE,
            false,
            null,
            slot,
            CatchUp.ONCE,
            null,
            null,
            NOW);
    when(triggers.find(OWNER, automationId, triggerId)).thenReturn(Optional.of(schedule));
    when(triggers.setEnabled(eq(OWNER), eq(automationId), eq(triggerId), any(Boolean.class), any()))
        .thenReturn(Optional.of(schedule));

    service.setEnabled(OWNER, automationId, triggerId, true);
    verify(triggers)
        .setEnabled(OWNER, automationId, triggerId, true, Instant.parse("2026-10-20T13:00:00Z"));
    service.setEnabled(OWNER, automationId, triggerId, false);
    verify(triggers).setEnabled(OWNER, automationId, triggerId, false, null);
  }

  @Test
  @DisplayName("deleting what the owner does not have is not found")
  void deleteNotFound() {
    UUID triggerId = UUID.randomUUID();
    when(triggers.delete(OWNER, automationId, triggerId)).thenReturn(false);
    assertThatThrownBy(() -> service.delete(OWNER, automationId, triggerId))
        .isInstanceOf(TriggerNotFoundException.class);
  }
}
