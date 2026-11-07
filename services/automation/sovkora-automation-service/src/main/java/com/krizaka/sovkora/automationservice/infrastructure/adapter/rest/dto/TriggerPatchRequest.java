package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

/**
 * The body that enables or disables a trigger.
 *
 * @param enabled the new state
 */
public record TriggerPatchRequest(Boolean enabled) {

  /**
   * The new state.
   *
   * @return the state, or an {@link IllegalArgumentException} when none was given
   */
  public boolean requireEnabled() {
    if (enabled == null) {
      throw new IllegalArgumentException("say whether the trigger is enabled");
    }
    return enabled;
  }
}
