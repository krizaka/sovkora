package com.krizaka.sovkora.knowledgeservice.analytics.application.service;

import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.AnalyticsSettings;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.DayPoint;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.Figure;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.Funnel;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.Insights;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.KindFigure;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.Range;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.ValueUnit;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.port.AnalyticsRepository;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.port.AnalyticsRepository.DayTotals;
import com.krizaka.sovkora.knowledgeservice.analytics.domain.port.AnalyticsRepository.KindTotals;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Insights and funnels (ADR-081 §3), computed on read from the tenant's events — counts and rates
 * only, and nothing reported for an audience under the tenant's {@code minAudience}.
 */
@Service
public class AnalyticsInsightService {

  private static final Logger logger = LoggerFactory.getLogger(AnalyticsInsightService.class);

  private final AnalyticsRepository repository;
  private final AnalyticsSettingsService settings;

  public AnalyticsInsightService(
      AnalyticsRepository repository, AnalyticsSettingsService settings) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** What the tenant's events say over the range, narrowed to an owner when one is given. */
  public Insights insights(String tenant, Range range, String owner) {
    AnalyticsSettings s = settings.forTenant(tenant);
    range.requireAtMostDays(s.maxRangeDays());
    long[] now = repository.totals(tenant, range, owner);
    if (now[1] < s.minAudience()) {
      logger.info("analytics.insights tenant={} owner={} suppressed=true", tenant, owner);
      return Insights.suppressed(range, owner, s.minAudience());
    }
    long[] before = repository.totals(tenant, range.previous(), owner);
    Map<String, KindTotals> current = repository.kindTotals(tenant, range, owner);
    Map<String, KindTotals> previous = repository.kindTotals(tenant, range.previous(), owner);
    Map<String, EventKind> kinds = settings.kinds(tenant);

    List<KindFigure> perKind = new ArrayList<>();
    current.forEach(
        (kind, t) -> {
          if (t.subjects() < s.minAudience()) {
            return;
          }
          ValueUnit unit = kinds.containsKey(kind) ? kinds.get(kind).unit() : ValueUnit.NONE;
          boolean measured = unit != ValueUnit.NONE;
          KindTotals was = previous.get(kind);
          perKind.add(
              new KindFigure(
                  kind,
                  unit,
                  Figure.of(t.count(), was == null ? 0 : was.count()),
                  t.subjects(),
                  measured ? t.valueSum() : null,
                  measured ? t.valueMean() : null));
        });
    perKind.sort(Comparator.comparingLong((KindFigure k) -> k.count().current()).reversed());

    List<DayPoint> series = new ArrayList<>();
    Map<LocalDate, DayTotals> days = repository.days(tenant, range, owner);
    LocalDate last = LocalDate.ofInstant(range.to().minusNanos(1), ZoneOffset.UTC);
    for (LocalDate day = LocalDate.ofInstant(range.from(), ZoneOffset.UTC);
        !day.isAfter(last);
        day = day.plusDays(1)) {
      DayTotals t = days.get(day);
      series.add(
          t == null
              ? DayPoint.of(day, 0, Map.of(), s.minAudience())
              : DayPoint.of(day, t.activeSubjects(), t.events(), s.minAudience()));
    }

    Instant cohortsSince =
        LocalDate.ofInstant(range.to(), ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(s.cohortWeeks() - 1L)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
    Insights insights =
        new Insights(
            range,
            owner,
            s.minAudience(),
            false,
            Figure.of(now[0], before[0]),
            Figure.of(now[1], before[1]),
            perKind,
            series,
            repository.topItems(
                tenant, range, owner, s.positiveKinds(), s.minAudience(), s.topItems()),
            repository.cohorts(tenant, cohortsSince, range.to(), owner, s.minAudience()));
    logger.info(
        "analytics.insights tenant={} owner={} days={} events={} subjects={}",
        tenant,
        owner,
        ChronoUnit.DAYS.between(range.from(), range.to()),
        now[0],
        now[1]);
    return insights;
  }

  /** The ordered funnel of the kinds over the range. */
  public Funnel funnel(String tenant, Range range, String owner, List<String> kinds) {
    AnalyticsSettings s = settings.forTenant(tenant);
    range.requireAtMostDays(s.maxRangeDays());
    Map<String, EventKind> known = settings.kinds(tenant);
    for (String kind : kinds) {
      if (!known.containsKey(kind)) {
        throw new IllegalArgumentException("unknown kind: " + kind);
      }
    }
    List<Long> reached = repository.funnel(tenant, range, owner, kinds, s.funnelWindowHours());
    logger.info("analytics.funnel tenant={} owner={} steps={}", tenant, owner, kinds.size());
    return Funnel.of(range, owner, s.funnelWindowHours(), s.minAudience(), kinds, reached);
  }
}
