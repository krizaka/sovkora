package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewTriggerTest {

  private static final ScheduleSpec SLOT =
      new ScheduleSpec(LocalTime.NOON, ZoneOffset.UTC, Set.of());

  @Test
  @DisplayName("a webhook has no schedule, a schedule needs one")
  void kinds() {
    assertThat(new NewTrigger(TriggerKind.WEBHOOK, null, null).catchUp()).isEqualTo(CatchUp.ONCE);
    assertThat(new NewTrigger(TriggerKind.SCHEDULE, SLOT, CatchUp.SKIP).catchUp())
        .isEqualTo(CatchUp.SKIP);
    assertThatThrownBy(() -> new NewTrigger(TriggerKind.SCHEDULE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NewTrigger(TriggerKind.WEBHOOK, SLOT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("kinds and catch-up policies are read case-insensitively and refused when unknown")
  void parsing() {
    assertThat(TriggerKind.parse(" Webhook ")).isEqualTo(TriggerKind.WEBHOOK);
    assertThat(CatchUp.parse("SKIP")).isEqualTo(CatchUp.SKIP);
    assertThat(CatchUp.parse(null)).isEqualTo(CatchUp.ONCE);
    assertThatThrownBy(() -> TriggerKind.parse("email"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TriggerKind.parse(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CatchUp.parse("later")).isInstanceOf(IllegalArgumentException.class);
  }
}
