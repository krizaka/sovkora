package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A partial change of an automation: a null field is left as it is. Optional on the wire (ADR-048).
 *
 * @param name a new label, or null
 * @param inputs new fixed inputs, or null
 * @param enabled a new state, or null
 */
public record AutomationChange(String name, Map<String, Object> inputs, Boolean enabled) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public AutomationChange {
    name = name == null ? null : NewAutomation.requireName(name);
    inputs = inputs == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
  }

  /**
   * Whether the change says nothing.
   *
   * @return true when every field is null
   */
  public boolean isEmpty() {
    return name == null && inputs == null && enabled == null;
  }
}
