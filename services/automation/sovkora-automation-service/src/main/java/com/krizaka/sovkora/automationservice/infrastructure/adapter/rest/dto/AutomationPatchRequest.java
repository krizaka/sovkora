package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.automationservice.domain.model.AutomationChange;
import java.util.Map;

/**
 * The body that changes an automation; a missing field is left as it is.
 *
 * @param name a new label
 * @param inputs new fixed inputs
 * @param enabled a new state
 */
public record AutomationPatchRequest(String name, Map<String, Object> inputs, Boolean enabled) {

  /**
   * The validated change.
   *
   * @return the change, or an {@link IllegalArgumentException} naming what is wrong
   */
  public AutomationChange toChange() {
    return new AutomationChange(name, inputs, enabled);
  }
}
