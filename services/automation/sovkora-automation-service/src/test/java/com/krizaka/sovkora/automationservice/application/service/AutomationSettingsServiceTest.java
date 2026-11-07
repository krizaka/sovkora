package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.AutomationSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationSettingsServiceTest {

  private final AutomationSettingsRepository repository = mock(AutomationSettingsRepository.class);
  private final AutomationSettingsService settings = new AutomationSettingsService(repository);

  @Test
  @DisplayName("an absent key is the default")
  void absent() {
    when(repository.intValue("maxAutomationsPerUser")).thenReturn(Optional.empty());
    assertThat(settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER))
        .isEqualTo(SettingKey.MAX_AUTOMATIONS_PER_OWNER.defaultValue());
  }

  @Test
  @DisplayName("a stored value is used, kept inside the bounds")
  void stored() {
    when(repository.intValue("maxAutomationsPerUser")).thenReturn(Optional.of(7));
    assertThat(settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER)).isEqualTo(7);
    when(repository.intValue("maxAutomationsPerUser")).thenReturn(Optional.of(99_999));
    assertThat(settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER)).isEqualTo(200);
  }
}
