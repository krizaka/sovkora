package com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.rest;

import com.krizaka.sovkora.knowledge.analytics.application.service.AnalyticsIngestService;
import com.krizaka.sovkora.knowledge.analytics.application.service.AnalyticsInsightService;
import com.krizaka.sovkora.knowledge.analytics.domain.model.ErasureResult;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Funnel;
import com.krizaka.sovkora.knowledge.analytics.domain.model.IngestResult;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Insights;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Range;
import com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.rest.dto.AnalyticsErasureRequest;
import com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.rest.dto.AnalyticsEventBatchRequest;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Identifiers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The analytics resource (ADR-081 §3), reached through the edge with a product's service key
 * (scopes {@code analytics:sync} and {@code analytics:read}). The tenant is the key's account — the
 * JWT subject — and every call stays inside it.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

  /** The range asked when none is: the last 28 days — a request default, not a parameter. */
  private static final int DEFAULT_DAYS = 28;

  private final AnalyticsIngestService ingest;
  private final AnalyticsInsightService insights;
  private final Clock clock;

  public AnalyticsController(
      AnalyticsIngestService ingest, AnalyticsInsightService insights, Clock clock) {
    this.ingest = Objects.requireNonNull(ingest, "ingest");
    this.insights = Objects.requireNonNull(insights, "insights");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Records a batch of events (idempotent by id). */
  @PostMapping("/events")
  public IngestResult events(
      @AuthenticationPrincipal Jwt jwt, @RequestBody AnalyticsEventBatchRequest body) {
    return ingest.record(tenant(jwt), body.toEvents());
  }

  /** Forgets subjects and items, in the analytics and the recommender's events. */
  @PostMapping("/erasures")
  public ErasureResult erasures(
      @AuthenticationPrincipal Jwt jwt, @RequestBody AnalyticsErasureRequest body) {
    return ingest.erase(tenant(jwt), body.subjectIds(), body.itemIds());
  }

  /**
   * KPIs, series, top items and cohorts over {@code [from, to)} (UTC dates, {@code to} excluded).
   */
  @GetMapping("/insights")
  public Insights insights(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(value = "from", required = false) String from,
      @RequestParam(value = "to", required = false) String to,
      @RequestParam(value = "owner", required = false) String owner) {
    return insights.insights(tenant(jwt), range(from, to), owner(owner));
  }

  /** The ordered funnel of {@code steps} over {@code [from, to)}. */
  @GetMapping("/funnel")
  public Funnel funnel(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("steps") String steps,
      @RequestParam(value = "from", required = false) String from,
      @RequestParam(value = "to", required = false) String to,
      @RequestParam(value = "owner", required = false) String owner) {
    return insights.funnel(tenant(jwt), range(from, to), owner(owner), Funnel.parseSteps(steps));
  }

  private Range range(String from, String to) {
    LocalDate end =
        to == null ? LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1) : date(to);
    LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS) : date(from);
    return new Range(instant(start), instant(end));
  }

  private static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("dates are YYYY-MM-DD", e);
    }
  }

  private static Instant instant(LocalDate day) {
    return day.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private static String owner(String owner) {
    return owner == null || owner.isBlank() ? null : Identifiers.require(owner, "owner");
  }

  private static String tenant(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw new IllegalStateException("no tenant: the request carries no session");
    }
    return jwt.getSubject();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
  }
}
