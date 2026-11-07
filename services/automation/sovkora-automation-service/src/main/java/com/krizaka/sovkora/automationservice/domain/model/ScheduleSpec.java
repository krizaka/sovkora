package com.krizaka.sovkora.automationservice.domain.model;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * « Daily at HH:mm in a time zone, on these weekdays » (ADR-079 §1). It validates itself and knows
 * its next slot; the zone is a real region id, so daylight saving is the zone's business.
 *
 * @param time the local time of the slot, to the minute
 * @param zone the time zone
 * @param days the weekdays it runs on; every day when empty
 */
public record ScheduleSpec(LocalTime time, ZoneId zone, Set<DayOfWeek> days) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public ScheduleSpec {
    if (time == null || zone == null) {
      throw new IllegalArgumentException("a schedule needs a time and a time zone");
    }
    time = time.truncatedTo(ChronoUnit.MINUTES);
    Set<DayOfWeek> chosen =
        days == null || days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(days);
    days = Collections.unmodifiableSet(chosen);
  }

  /**
   * Builds a schedule from its wire form.
   *
   * @param time {@code HH:mm}
   * @param zone a region id such as {@code America/Toronto}
   * @param days weekday names such as {@code MON}; null or empty for every day
   * @return the schedule
   * @throws IllegalArgumentException when any part is not readable
   */
  public static ScheduleSpec parse(String time, String zone, Collection<String> days) {
    if (time == null || zone == null) {
      throw new IllegalArgumentException("a schedule needs a time and a time zone");
    }
    try {
      Set<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
      if (days != null) {
        for (String day : days) {
          parsed.add(parseDay(day));
        }
      }
      return new ScheduleSpec(LocalTime.parse(time.strip()), ZoneId.of(zone.strip()), parsed);
    } catch (DateTimeException invalid) {
      throw new IllegalArgumentException(
          "the schedule needs a time as HH:mm and a region time zone such as America/Toronto");
    }
  }

  private static DayOfWeek parseDay(String day) {
    if (day == null || day.length() < 3) {
      throw new IllegalArgumentException("a weekday is MON, TUE, WED, THU, FRI, SAT or SUN");
    }
    String prefix = day.strip().substring(0, 3).toUpperCase(Locale.ROOT);
    for (DayOfWeek candidate : DayOfWeek.values()) {
      if (candidate.name().startsWith(prefix)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("a weekday is MON, TUE, WED, THU, FRI, SAT or SUN");
  }

  /**
   * The first slot strictly after an instant.
   *
   * @param after the instant
   * @return the next slot
   */
  public Instant nextAfter(Instant after) {
    ZonedDateTime local = after.atZone(zone);
    for (int offset = 0; offset <= 8; offset++) {
      ZonedDateTime candidate = local.toLocalDate().plusDays(offset).atTime(time).atZone(zone);
      if (candidate.toInstant().isAfter(after) && days.contains(candidate.getDayOfWeek())) {
        return candidate.toInstant();
      }
    }
    throw new IllegalStateException("a schedule with weekdays always has a slot within a week");
  }

  /**
   * The weekdays as the wire shows them.
   *
   * @return {@code MON}…{@code SUN}, in week order
   */
  public List<String> dayNames() {
    return days.stream().sorted().map(day -> day.name().substring(0, 3)).toList();
  }
}
