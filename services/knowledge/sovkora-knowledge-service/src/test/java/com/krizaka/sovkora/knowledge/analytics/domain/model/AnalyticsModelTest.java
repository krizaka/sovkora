package com.krizaka.sovkora.knowledge.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsModelTest {

  private static final String SUBJECT = "a3f1c9e2b7d84f60a3f1c9e2b7d84f60";
  private static final Instant AT = Instant.parse("2026-10-06T10:00:00Z");

  @Test
  @DisplayName("an event carries a pseudonym — an e-mail or a name never fits")
  void pseudonymOnly() {
    assertThat(new AnalyticsEvent("e1", SUBJECT, "talk", null, null, 120.0, AT).kind())
        .isEqualTo("talk");
    assertThatThrownBy(
            () -> new AnalyticsEvent("e1", "ana@example.com", "talk", null, null, 1.0, AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pseudonym");
    assertThatThrownBy(() -> new AnalyticsEvent("e1", "Ana Tremblay", "talk", null, null, 1.0, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a kind is lower snake case and a value is finite and not negative")
  void kindAndValue() {
    assertThatThrownBy(() -> new AnalyticsEvent("e1", SUBJECT, "Talk!", null, null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalyticsEvent("e1", SUBJECT, "talk", null, null, -1.0, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalyticsEvent("e1", SUBJECT, "talk", null, null, Double.NaN, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a range is half-open, knows the period before it, and is bounded")
  void range() {
    Range r =
        new Range(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z"));
    assertThat(r.previous())
        .isEqualTo(
            new Range(
                Instant.parse("2026-09-17T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")));
    assertThat(r.requireAtMostDays(14)).isSameAs(r);
    assertThatThrownBy(() -> r.requireAtMostDays(13)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Range(r.to(), r.from()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a figure's change is relative to the previous period, absent from nothing")
  void figure() {
    assertThat(Figure.of(15, 10).change()).isEqualTo(0.5);
    assertThat(Figure.of(5, 10).change()).isEqualTo(-0.5);
    assertThat(Figure.of(5, 0).change()).isNull();
  }

  @Test
  @DisplayName("a day under the audience threshold carries no figure")
  void daySuppressed() {
    LocalDate day = LocalDate.parse("2026-10-06");
    assertThat(DayPoint.of(day, 2, Map.of("view", 7L), 3).suppressed()).isTrue();
    assertThat(DayPoint.of(day, 2, Map.of("view", 7L), 3).events()).isEmpty();
    assertThat(DayPoint.of(day, 3, Map.of("view", 7L), 3).activeSubjects()).isEqualTo(3);
  }

  @Test
  @DisplayName("a cohort's retention is the share of its first week active each week after")
  void cohort() {
    Cohort c = Cohort.of(LocalDate.parse("2026-09-28"), 4, List.of(4L, 2L, 1L));
    assertThat(c.retention()).containsExactly(1.0, 0.5, 0.25);
  }

  @Test
  @DisplayName("funnel steps: 2 to 6 distinct kinds")
  void funnelSteps() {
    assertThat(Funnel.parseSteps("view, like,tip")).containsExactly("view", "like", "tip");
    assertThatThrownBy(() -> Funnel.parseSteps("view"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Funnel.parseSteps("a1,b1,c1,d1,e1,f1,g1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Funnel.parseSteps("view,like,view"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Funnel.parseSteps("view,DROP TABLE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a funnel converts step to step, and hides a step under the threshold")
  void funnel() {
    Range r =
        new Range(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z"));
    Funnel f = Funnel.of(r, null, 168, 3, List.of("view", "like", "tip"), List.of(8L, 4L, 1L));
    assertThat(f.steps().getFirst().subjects()).isEqualTo(8);
    assertThat(f.steps().getFirst().fromPrevious()).isNull();
    assertThat(f.steps().get(1).fromPrevious()).isEqualTo(0.5);
    assertThat(f.steps().get(1).fromFirst()).isEqualTo(0.5);
    assertThat(f.steps().get(2).suppressed()).isTrue();
    assertThat(f.steps().get(2).subjects()).isNull();
  }

  @Test
  @DisplayName("settings outside their bounds fail loudly")
  void settingsBounds() {
    assertThatThrownBy(() -> new AnalyticsSettings(1, 180, 1, 366, 8, 168, 10, List.of("like")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("minAudience");
    assertThat(new AnalyticsSettings(1, 180, 3, 366, 8, 168, 10, List.of("like")).minAudience())
        .isEqualTo(3);
  }
}
