package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.AutomationSettingsRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** The rule set: a stored value clamped to the bounds of its key, or the key's default. */
@Service
public class AutomationSettingsService {

  private final AutomationSettingsRepository settings;

  public AutomationSettingsService(AutomationSettingsRepository settings) {
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsRepository cannot be null");
  }

  /**
   * The value of a setting.
   *
   * @param key the setting
   * @return the stored value kept inside the bounds, or the default when the row is absent
   */
  public int value(SettingKey key) {
    return settings.intValue(key.key()).map(key::clamp).orElse(key.defaultValue());
  }
}
