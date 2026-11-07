package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.automationservice.domain.model.NewAutomation;
import java.util.Map;

/**
 * The body that creates an automation.
 *
 * @param name the label
 * @param studioKey the Studio to run
 * @param inputs the fixed answers to its form, optional
 * @param enabled whether it may start runs, optional (true)
 */
public record AutomationRequest(
    String name, String studioKey, Map<String, Object> inputs, Boolean enabled) {

  /**
   * The validated draft.
   *
   * @return the draft, or an {@link IllegalArgumentException} naming what is wrong
   */
  public NewAutomation toDraft() {
    return new NewAutomation(name, studioKey, inputs, enabled);
  }
}
