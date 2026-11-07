package com.krizaka.sovkora.automationservice.domain.port;

import java.util.Optional;

/** Reads the rule set rows of {@code automation_settings}. */
public interface AutomationSettingsRepository {

  /**
   * One integer setting.
   *
   * @param key the row key
   * @return the stored value, empty when the row is absent
   */
  Optional<Integer> intValue(String key);
}
