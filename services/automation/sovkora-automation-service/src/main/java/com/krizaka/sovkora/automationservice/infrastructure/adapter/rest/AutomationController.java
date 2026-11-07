package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.automationservice.application.service.AutomationService;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.AutomationPatchRequest;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.AutomationRequest;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.AutomationResponse;
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
 * The caller's automations: what runs (a Studio and its fixed inputs). Authentication is the
 * session JWT, and every statement is scoped by its subject — the caller is the owner.
 */
@RestController
@RequestMapping("/api/v1/automations")
class AutomationController {

  private final AutomationService automations;

  AutomationController(AutomationService automations) {
    this.automations = Objects.requireNonNull(automations, "AutomationService cannot be null");
  }

  /**
   * Creates an automation.
   *
   * @param request the label, the Studio and its fixed inputs
   * @param actor the authenticated caller
   * @return {@code 201} with the automation
   */
  @PostMapping
  ResponseEntity<AutomationResponse> create(
      @RequestBody AutomationRequest request, @AuthenticationPrincipal Jwt actor) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(AutomationResponse.from(automations.create(actor.getSubject(), request.toDraft())));
  }

  /**
   * The caller's automations.
   *
   * @param actor the authenticated caller
   * @return the automations, oldest first
   */
  @GetMapping
  List<AutomationResponse> list(@AuthenticationPrincipal Jwt actor) {
    return automations.list(actor.getSubject()).stream().map(AutomationResponse::from).toList();
  }

  /**
   * One automation.
   *
   * @param id the automation
   * @param actor the authenticated caller
   * @return the automation; {@code 404} when the caller owns none with this id
   */
  @GetMapping("/{id}")
  AutomationResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
    return AutomationResponse.from(automations.get(actor.getSubject(), id));
  }

  /**
   * Changes an automation; a missing field is left as it is.
   *
   * @param id the automation
   * @param request what to change
   * @param actor the authenticated caller
   * @return the changed automation
   */
  @PatchMapping("/{id}")
  AutomationResponse update(
      @PathVariable UUID id,
      @RequestBody AutomationPatchRequest request,
      @AuthenticationPrincipal Jwt actor) {
    return AutomationResponse.from(automations.update(actor.getSubject(), id, request.toChange()));
  }

  /**
   * Removes an automation with its triggers and history.
   *
   * @param id the automation
   * @param actor the authenticated caller
   * @return {@code 204}
   */
  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
    automations.delete(actor.getSubject(), id);
    return ResponseEntity.noContent().build();
  }
}
