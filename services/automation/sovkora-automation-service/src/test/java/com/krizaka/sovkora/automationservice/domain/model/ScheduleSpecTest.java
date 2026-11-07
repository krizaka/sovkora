package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduleSpecTest {

  private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

  @Test
  @DisplayName("the next slot is the first one strictly after now, in the schedule's own zone")
  void nextSlot() {
    ScheduleSpec daily = new ScheduleSpec(LocalTime.of(8, 0), TORONTO, Set.of());
    // 2026-10-20 11:00 UTC is 07:00 in Toronto (EDT): 08:00 local is the same day, 12:00 UTC.
    assertThat(daily.nextAfter(Instant.parse("2026-10-20T11:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
    // exactly on the slot: strictly after, so the next day.
    assertThat(daily.nextAfter(Instant.parse("2026-10-20T12:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));
  }

  @Test
  @DisplayName("only the chosen weekdays have a slot")
  void weekdays() {
    ScheduleSpec monday = new ScheduleSpec(LocalTime.of(8, 0), TORONTO, Set.of(DayOfWeek.MONDAY));
    // 2026-10-20 is a Tuesday: the next Monday is 2026-10-26.
    assertThat(monday.nextAfter(Instant.parse("2026-10-20T11:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
  }

  @Test
  @DisplayName("daylight saving moves the UTC instant, not the local time")
  void daylightSaving() {
    ScheduleSpec daily = new ScheduleSpec(LocalTime.of(8, 0), TORONTO, Set.of());
    // Clocks go back on 2026-11-01: 08:00 local is 12:00Z before, 13:00Z after.
    assertThat(daily.nextAfter(Instant.parse("2026-10-31T13:00:00Z")))
        .isEqualTo(Instant.parse("2026-11-01T13:00:00Z"));
  }

  @Test
  @DisplayName("it reads its wire form and says its weekdays in week order")
  void parse() {
    ScheduleSpec spec = ScheduleSpec.parse("08:30", "America/Toronto", List.of("fri", "Monday"));
    assertThat(spec.time()).isEqualTo(LocalTime.of(8, 30));
    assertThat(spec.dayNames()).containsExactly("MON", "FRI");
    assertThat(ScheduleSpec.parse("08:30", "UTC", null).dayNames()).hasSize(7);
  }

  @Test
  @DisplayName("an unreadable time, zone or weekday is refused")
  void refuses() {
    assertThatThrownBy(() -> ScheduleSpec.parse("8am", "UTC", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScheduleSpec.parse("08:00", "Mars/Olympus", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScheduleSpec.parse("08:00", "UTC", List.of("someday")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScheduleSpec.parse(null, "UTC", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
