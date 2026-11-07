package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.automationservice.application.service.TriggerService;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.TriggerPatchRequest;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.TriggerRequest;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.TriggerResponse;
import com.krizaka.sovkora.automationservice.infrastructure.config.PublicUrlProperties;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The triggers of one of the caller's automations: a webhook (signed, with a secret shown once) or
 * a schedule. Authenticated like the rest of the API; the inbound call itself is {@code
 * WebhookIngressController}.
 */
@RestController
@RequestMapping("/api/v1/automations/{automationId}/triggers")
class TriggerController {

  private final TriggerService triggers;
  private final PublicUrlProperties urls;

  TriggerController(TriggerService triggers, PublicUrlProperties urls) {
    this.triggers = Objects.requireNonNull(triggers, "TriggerService cannot be null");
    this.urls = Objects.requireNonNull(urls, "PublicUrlProperties cannot be null");
  }

  /**
   * Adds a trigger.
   *
   * @param automationId the automation
   * @param request the kind and, for a schedule, its slot
   * @param actor the authenticated caller
   * @return {@code 201} with the trigger; a webhook's answer carries its secret, shown once
   */
  @PostMapping
  ResponseEntity<TriggerResponse> create(
      @PathVariable UUID automationId,
      @RequestBody TriggerRequest request,
      @AuthenticationPrincipal Jwt actor) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            TriggerResponse.of(
                triggers.create(actor.getSubject(), automationId, request.toDraft()), urls));
  }

  /**
   * The triggers of the automation (no secrets).
   *
   * @param automationId the automation
   * @param actor the authenticated caller
   * @return the triggers, oldest first
   */
  @GetMapping
  List<TriggerResponse> list(@PathVariable UUID automationId, @AuthenticationPrincipal Jwt actor) {
    return triggers.list(actor.getSubject(), automationId).stream()
        .map(trigger -> TriggerResponse.from(trigger, urls))
        .toList();
  }

  /**
   * Enables or disables a trigger.
   *
   * @param automationId the automation
   * @param triggerId the trigger
   * @param request the new state
   * @param actor the authenticated caller
   * @return the trigger
   */
  @PatchMapping("/{triggerId}")
  TriggerResponse update(
      @PathVariable UUID automationId,
      @PathVariable UUID triggerId,
      @RequestBody TriggerPatchRequest request,
      @AuthenticationPrincipal Jwt actor) {
    return TriggerResponse.from(
        triggers.setEnabled(actor.getSubject(), automationId, triggerId, request.requireEnabled()),
        urls);
  }

  /**
   * Issues a new secret. The previous one keeps verifying for the grace period.
   *
   * @param automationId the automation
   * @param triggerId the trigger
   * @param actor the authenticated caller
   * @return the trigger with its new secret, shown once
   */
  @PostMapping("/{triggerId}/rotate")
  TriggerResponse rotate(
      @PathVariable UUID automationId,
      @PathVariable UUID triggerId,
      @AuthenticationPrincipal Jwt actor) {
    return TriggerResponse.of(triggers.rotate(actor.getSubject(), automationId, triggerId), urls);
  }

  /**
   * Removes a trigger; its runs stay in the history.
   *
   * @param automationId the automation
   * @param triggerId the trigger
   * @param actor the authenticated caller
   * @return {@code 204}
   */
  @DeleteMapping("/{triggerId}")
  ResponseEntity<Void> delete(
      @PathVariable UUID automationId,
      @PathVariable UUID triggerId,
      @AuthenticationPrincipal Jwt actor) {
    triggers.delete(actor.getSubject(), automationId, triggerId);
    return ResponseEntity.noContent().build();
  }
}
