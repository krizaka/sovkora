package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.automationservice.domain.model.Automation;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An automation as the API shows it (never the owner id: the caller is the owner).
 *
 * @param id the automation
 * @param name the label
 * @param studioKey the Studio it runs
 * @param inputs the fixed inputs
 * @param enabled whether it may start runs
 * @param createdAt when it was created
 * @param updatedAt when it last changed
 */
public record AutomationResponse(
    UUID id,
    String name,
    String studioKey,
    Map<String, Object> inputs,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Maps the domain automation.
   *
   * @param automation the automation
   * @return its response
   */
  public static AutomationResponse from(Automation automation) {
    return new AutomationResponse(
        automation.id(),
        automation.name(),
        automation.studioKey(),
        automation.inputs(),
        automation.enabled(),
        automation.createdAt(),
        automation.updatedAt());
  }
}
