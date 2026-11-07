package com.krizaka.sovkora.knowledge.analytics.domain.port;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsEvent;
import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsSettings;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Cohort;
import com.krizaka.sovkora.knowledge.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Range;
import com.krizaka.sovkora.knowledge.analytics.domain.model.TopItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The analytics store (ADR-081): every method is scoped to one tenant. */
public interface AnalyticsRepository {

  /** The newest settings of the tenant, else of every tenant ({@code '*'}). */
  Optional<AnalyticsSettings> settings(String tenant);

  /** The kinds the tenant may send: its own and every tenant's, its own winning. */
  Map<String, EventKind> kinds(String tenant);

  /** Inserts the events; a known id is ignored. Returns how many were new. */
  int insertEvents(String tenant, List<AnalyticsEvent> events);

  /** Deletes the subjects' and the items' events in both tables. Returns the rows deleted. */
  int erase(String tenant, Collection<String> subjects, Collection<String> items);

  /** Events and distinct subjects over the range: {@code [events, subjects]}. */
  long[] totals(String tenant, Range range, String owner);

  /** Per kind: {@code count, subjects, valueSum, valueMean}, kinds with no event left out. */
  Map<String, KindTotals> kindTotals(String tenant, Range range, String owner);

  /** Per day (UTC): distinct subjects, and events per kind. */
  Map<LocalDate, DayTotals> days(String tenant, Range range, String owner);

  /** The items most subjects engaged positively, each with at least {@code minAudience}. */
  List<TopItem> topItems(
      String tenant,
      Range range,
      String owner,
      Collection<String> positiveKinds,
      int minAudience,
      int limit);

  /** Weekly cohorts first seen since {@code since}, each with at least {@code minAudience}. */
  List<Cohort> cohorts(String tenant, Instant since, Instant until, String owner, int minAudience);

  /** Subjects reaching each step of the funnel. */
  List<Long> funnel(String tenant, Range range, String owner, List<String> kinds, int windowHours);

  /** The tenants that have analytics events. */
  List<String> tenants();

  /** Deletes the tenant's analytics events older than {@code before}. */
  int purge(String tenant, Instant before);

  /**
   * One kind's totals.
   *
   * @param count events
   * @param subjects distinct subjects
   * @param valueSum the sum of the values, when any was sent
   * @param valueMean their mean
   */
  record KindTotals(long count, long subjects, Double valueSum, Double valueMean) {}

  /**
   * One day's totals.
   *
   * @param activeSubjects distinct subjects
   * @param events events per kind
   */
  record DayTotals(long activeSubjects, Map<String, Long> events) {}
}
