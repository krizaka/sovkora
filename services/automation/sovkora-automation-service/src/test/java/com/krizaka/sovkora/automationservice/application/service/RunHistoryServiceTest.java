package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.RunNotFoundException;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import com.krizaka.sovkora.automationservice.domain.port.RunHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunHistoryServiceTest {

  private final RunHistoryRepository runs = mock(RunHistoryRepository.class);
  private final AutomationRepository automations = mock(AutomationRepository.class);
  private final RunHistoryService history = new RunHistoryService(runs, automations);

  @Test
  @DisplayName("the page is 50 by default and never more than 200")
  void pageBounds() {
    when(runs.list("o", null, 50)).thenReturn(List.of());
    assertThat(history.list("o", null, null)).isEmpty();
    history.list("o", null, 0);
    history.list("o", null, 10_000);
    verify(runs).list("o", null, 200);
    verify(runs, org.mockito.Mockito.times(2)).list("o", null, 50);
  }

  @Test
  @DisplayName("the runs of an automation the owner does not have are not listed")
  void foreignAutomation() {
    UUID automation = UUID.randomUUID();
    when(automations.find("o", automation)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> history.list("o", automation, null))
        .isInstanceOf(AutomationNotFoundException.class);
    verify(runs, never()).list(any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  @DisplayName("a run of someone else is not found")
  void foreignRun() {
    UUID run = UUID.randomUUID();
    when(runs.find("o", run)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> history.get("o", run)).isInstanceOf(RunNotFoundException.class);
  }
}
