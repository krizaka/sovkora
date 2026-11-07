package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyAutomationsException;
import com.krizaka.sovkora.automationservice.domain.model.Automation;
import com.krizaka.sovkora.automationservice.domain.model.AutomationChange;
import com.krizaka.sovkora.automationservice.domain.model.NewAutomation;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AutomationServiceTest {

  private static final String OWNER = "owner-1";

  private final AutomationRepository repository = mock(AutomationRepository.class);
  private final AutomationSettingsService settings = mock(AutomationSettingsService.class);
  private final AutomationService service =
      new AutomationService(repository, settings, new ObjectMapper());

  private static Automation automation(UUID id) {
    return new Automation(id, OWNER, "n", "k", Map.of(), true, Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  @DisplayName("creating under the quota stores the draft for the owner")
  void creates() {
    NewAutomation draft = new NewAutomation("n", "k", Map.of("a", 1), null);
    when(settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER)).thenReturn(2);
    when(settings.value(SettingKey.MAX_INPUTS_BYTES)).thenReturn(1000);
    when(repository.count(OWNER)).thenReturn(1);
    Automation stored = automation(UUID.randomUUID());
    when(repository.create(OWNER, draft)).thenReturn(stored);

    assertThat(service.create(OWNER, draft)).isSameAs(stored);
  }

  @Test
  @DisplayName("at the quota nothing is stored")
  void quota() {
    when(settings.value(SettingKey.MAX_AUTOMATIONS_PER_OWNER)).thenReturn(2);
    when(settings.value(SettingKey.MAX_INPUTS_BYTES)).thenReturn(1000);
    when(repository.count(OWNER)).thenReturn(2);

    assertThatThrownBy(() -> service.create(OWNER, new NewAutomation("n", "k", null, null)))
        .isInstanceOf(TooManyAutomationsException.class);
    verify(repository, never()).create(any(), any());
  }

  @Test
  @DisplayName("inputs over the rule set's size are refused before anything is counted or stored")
  void inputsTooLarge() {
    when(settings.value(SettingKey.MAX_INPUTS_BYTES)).thenReturn(20);
    NewAutomation draft = new NewAutomation("n", "k", Map.of("a", "x".repeat(50)), null);

    assertThatThrownBy(() -> service.create(OWNER, draft))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("20 bytes");
    verify(repository, never()).create(any(), any());
  }

  @Test
  @DisplayName("a read of an automation the owner does not have is not found")
  void notFound() {
    UUID id = UUID.randomUUID();
    when(repository.find(OWNER, id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(OWNER, id))
        .isInstanceOf(AutomationNotFoundException.class);
  }

  @Test
  @DisplayName("an empty change is a read, not a write")
  void emptyChange() {
    UUID id = UUID.randomUUID();
    Automation existing = automation(id);
    when(repository.find(OWNER, id)).thenReturn(Optional.of(existing));

    assertThat(service.update(OWNER, id, new AutomationChange(null, null, null)))
        .isSameAs(existing);
    verify(repository, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("a change of an automation the owner does not have is not found")
  void updateNotFound() {
    UUID id = UUID.randomUUID();
    AutomationChange change = new AutomationChange(null, null, false);
    when(repository.update(OWNER, id, change)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(OWNER, id, change))
        .isInstanceOf(AutomationNotFoundException.class);
  }

  @Test
  @DisplayName("deleting what the owner does not have is not found")
  void deleteNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.delete(OWNER, id)).thenReturn(false);
    assertThatThrownBy(() -> service.delete(OWNER, id))
        .isInstanceOf(AutomationNotFoundException.class);
  }
}
