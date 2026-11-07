package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.krizaka.sovkora.automationservice.domain.model.CreatedTrigger;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.Trigger;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import com.krizaka.sovkora.automationservice.infrastructure.config.PublicUrlProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A trigger. {@code secret} is present only in the answer that creates the trigger or rotates its
 * secret, and cannot be read again; {@code secretHint} is its last characters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriggerResponse(
    UUID id,
    UUID automationId,
    String kind,
    boolean enabled,
    String webhookUrl,
    String secretHint,
    String secret,
    ScheduleView schedule,
    String catchUp,
    Instant nextFireAt,
    Instant lastFiredAt,
    Instant createdAt) {

  /**
   * The slot of a schedule trigger.
   *
   * @param time {@code HH:mm}
   * @param zone the time zone
   * @param days {@code MON}…{@code SUN}
   */
  public record ScheduleView(String time, String zone, List<String> days) {}

  /**
   * Shows a trigger without its secret.
   *
   * @param trigger the trigger
   * @param urls where senders call
   * @return the response
   */
  public static TriggerResponse from(Trigger trigger, PublicUrlProperties urls) {
    return of(new CreatedTrigger(trigger, null), urls);
  }

  /**
   * Shows a trigger with its one-time secret, if it has one.
   *
   * @param created the trigger and its secret
   * @param urls where senders call
   * @return the response
   */
  public static TriggerResponse of(CreatedTrigger created, PublicUrlProperties urls) {
    Trigger trigger = created.trigger();
    ScheduleSpec slot = trigger.schedule();
    return new TriggerResponse(
        trigger.id(),
        trigger.automationId(),
        trigger.kind().name().toLowerCase(java.util.Locale.ROOT),
        trigger.enabled(),
        trigger.kind() == TriggerKind.WEBHOOK ? urls.webhookUrl(trigger.id()) : null,
        trigger.secretHint(),
        created.secret(),
        slot == null
            ? null
            : new ScheduleView(slot.time().toString(), slot.zone().getId(), slot.dayNames()),
        trigger.catchUp().name().toLowerCase(java.util.Locale.ROOT),
        trigger.nextFireAt(),
        trigger.lastFiredAt(),
        trigger.createdAt());
  }
}
