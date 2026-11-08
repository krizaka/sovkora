package com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.persistence;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsEvent;
import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsSettings;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Cohort;
import com.krizaka.sovkora.knowledge.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledge.analytics.domain.model.KindSource;
import com.krizaka.sovkora.knowledge.analytics.domain.model.Range;
import com.krizaka.sovkora.knowledge.analytics.domain.model.TopItem;
import com.krizaka.sovkora.knowledge.analytics.domain.model.ValueUnit;
import com.krizaka.sovkora.knowledge.analytics.domain.port.AnalyticsRepository;
import com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.persistence.PgArrays;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The analytics store on {@code orazaka_knowledge_db} (ADR-081). Every read goes through the {@code
 * analytics_event_all} view (the analytics events and the recommender's); parameterised SQL only,
 * every statement scoped by {@code tenant_id}, the owner filter optional.
 */
@Repository
class JdbcAnalyticsRepositoryAdapter implements AnalyticsRepository {

  private static final String FROM_ANALYTICS_EVENT_ALL_WHERE = " FROM analytics_event_all WHERE";

  /** The scope every read starts from: tenant, range, and the owner when one is given. */
  private static final String SCOPE =
      " tenant_id = ? AND occurred_at >= ? AND occurred_at < ?"
          + " AND (CAST(? AS text) IS NULL OR owner_id = CAST(? AS text))";

  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  JdbcAnalyticsRepositoryAdapter(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.json = Objects.requireNonNull(json, "json");
  }

  @Override
  public Optional<AnalyticsSettings> settings(String tenant) {
    return jdbc
        .query(
            "SELECT version, settings::text FROM analytics_settings WHERE tenant_id IN (?, '*')"
                + " ORDER BY (tenant_id = '*'), version DESC LIMIT 1",
            (rs, n) ->
                json.readValue(rs.getString(2), AnalyticsSettingsDocument.class)
                    .toSettings(rs.getInt(1)),
            tenant)
        .stream()
        .findFirst();
  }

  @Override
  public Map<String, EventKind> kinds(String tenant) {
    Map<String, EventKind> kinds = new LinkedHashMap<>();
    // '*' first, the tenant's own rows after: its own definition of a kind wins.
    jdbc.query(
        "SELECT kind, unit::text, source::text FROM analytics_event_kind"
            + " WHERE tenant_id IN (?, '*') ORDER BY (tenant_id <> '*'), kind",
        rs -> {
          EventKind kind =
              new EventKind(
                  rs.getString(1),
                  ValueUnit.valueOf(rs.getString(2).toUpperCase(Locale.ROOT)),
                  KindSource.valueOf(rs.getString(3).toUpperCase(Locale.ROOT)));
          kinds.put(kind.kind(), kind);
        },
        tenant);
    return kinds;
  }

  @Override
  public int insertEvents(String tenant, List<AnalyticsEvent> events) {
    int[][] counts =
        jdbc.batchUpdate(
            "INSERT INTO analytics_event (tenant_id, event_id, subject_id, kind, item_id,"
                + " owner_id, value, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (tenant_id, event_id) DO NOTHING",
            events,
            100,
            (ps, e) -> {
              ps.setString(1, tenant);
              ps.setString(2, e.eventId());
              ps.setString(3, e.subjectId());
              ps.setString(4, e.kind());
              ps.setString(5, e.itemId());
              ps.setString(6, e.ownerId());
              if (e.value() == null) {
                ps.setNull(7, Types.DOUBLE);
              } else {
                ps.setDouble(7, e.value());
              }
              ps.setTimestamp(8, Timestamp.from(e.occurredAt()));
            });
    int accepted = 0;
    for (int[] batch : counts) {
      for (int c : batch) {
        accepted += Math.max(0, c);
      }
    }
    return accepted;
  }

  @Override
  public int erase(String tenant, Collection<String> subjects, Collection<String> items) {
    String people = PgArrays.text(subjects);
    String things = PgArrays.text(items);
    // One statement, so the four changes are one transaction; PostgreSQL runs every
    // data-modifying CTE whether the final SELECT reads it or not.
    Integer rows =
        jdbc.queryForObject(
            "WITH a AS (DELETE FROM analytics_event WHERE tenant_id = ?"
                + "   AND (subject_id = ANY(?::text[]) OR item_id = ANY(?::text[])) RETURNING 1),"
                + " r AS (DELETE FROM reco_event WHERE tenant_id = ?"
                + "   AND (user_id = ANY(?::text[]) OR item_id = ANY(?::text[])) RETURNING 1),"
                + " p AS (DELETE FROM reco_user_profile WHERE tenant_id = ?"
                + "   AND user_id = ANY(?::text[]) RETURNING 1),"
                + " q AS (UPDATE reco_request SET user_id = NULL WHERE tenant_id = ?"
                + "   AND user_id = ANY(?::text[]) RETURNING 1)"
                + " SELECT (SELECT count(*) FROM a) + (SELECT count(*) FROM r)",
            Integer.class,
            tenant,
            people,
            things,
            tenant,
            people,
            things,
            tenant,
            people,
            tenant,
            people);
    return rows == null ? 0 : rows;
  }

  @Override
  public long[] totals(String tenant, Range range, String owner) {
    return jdbc.queryForObject(
        "SELECT count(*), count(DISTINCT subject_id)" + FROM_ANALYTICS_EVENT_ALL_WHERE + SCOPE,
        (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)},
        scope(tenant, range, owner));
  }

  @Override
  public Map<String, KindTotals> kindTotals(String tenant, Range range, String owner) {
    Map<String, KindTotals> out = new LinkedHashMap<>();
    jdbc.query(
        "SELECT kind, count(*), count(DISTINCT subject_id), sum(value), avg(value)"
            + FROM_ANALYTICS_EVENT_ALL_WHERE
            + SCOPE
            + " GROUP BY kind ORDER BY 2 DESC, kind",
        rs -> {
          out.put(
              rs.getString(1),
              new KindTotals(
                  rs.getLong(2),
                  rs.getLong(3),
                  rounded(rs.getObject(4, Double.class)),
                  rounded(rs.getObject(5, Double.class))));
        },
        scope(tenant, range, owner));
    return out;
  }

  @Override
  public Map<LocalDate, DayTotals> days(String tenant, Range range, String owner) {
    Map<LocalDate, Long> active = new TreeMap<>();
    jdbc.query(
        "SELECT (occurred_at AT TIME ZONE 'UTC')::date, count(DISTINCT subject_id)"
            + FROM_ANALYTICS_EVENT_ALL_WHERE
            + SCOPE
            + " GROUP BY 1",
        rs -> {
          active.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
        },
        scope(tenant, range, owner));
    Map<LocalDate, Map<String, Long>> perKind = new TreeMap<>();
    jdbc.query(
        "SELECT (occurred_at AT TIME ZONE 'UTC')::date, kind, count(*)"
            + FROM_ANALYTICS_EVENT_ALL_WHERE
            + SCOPE
            + " GROUP BY 1, 2 ORDER BY 1, 2",
        rs -> {
          perKind
              .computeIfAbsent(rs.getObject(1, LocalDate.class), d -> new LinkedHashMap<>())
              .put(rs.getString(2), rs.getLong(3));
        },
        scope(tenant, range, owner));
    Map<LocalDate, DayTotals> out = new TreeMap<>();
    active.forEach(
        (day, subjects) ->
            out.put(day, new DayTotals(subjects, perKind.getOrDefault(day, Map.of()))));
    return out;
  }

  @Override
  public List<TopItem> topItems(
      String tenant,
      Range range,
      String owner,
      Collection<String> positiveKinds,
      int minAudience,
      int limit) {
    String positive = PgArrays.text(positiveKinds);
    List<Object> args = new ArrayList<>(List.of(positive, positive));
    // Arrays.asList, not List.of: the owner is null when the whole product is asked for.
    args.addAll(Arrays.asList(scope(tenant, range, owner)));
    args.add(positive);
    args.add(minAudience);
    args.add(limit);
    return jdbc.query(
        "SELECT item_id, max(owner_id),"
            + " count(DISTINCT subject_id) FILTER (WHERE kind = ANY(?::text[])),"
            + " count(*) FILTER (WHERE kind = ANY(?::text[])),"
            + " count(*) FILTER (WHERE kind = 'view'),"
            + " avg(value) FILTER (WHERE kind = 'view' AND value IS NOT NULL)"
            + FROM_ANALYTICS_EVENT_ALL_WHERE
            + SCOPE
            + " AND item_id IS NOT NULL GROUP BY item_id"
            + " HAVING count(DISTINCT subject_id) FILTER (WHERE kind = ANY(?::text[])) >= ?"
            + " ORDER BY 3 DESC, 4 DESC, item_id LIMIT ?",
        (rs, n) ->
            new TopItem(
                rs.getString(1),
                rs.getString(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rounded(rs.getObject(6, Double.class))),
        args.toArray());
  }

  @Override
  public List<Cohort> cohorts(
      String tenant, Instant since, Instant until, String owner, int minAudience) {
    Map<LocalDate, Map<Integer, Long>> grid = new TreeMap<>();
    jdbc.query(
        "WITH firsts AS (   SELECT subject_id, date_trunc('week', min(occurred_at) AT TIME ZONE"
            + " 'UTC') AS cw   FROM analytics_event_all WHERE tenant_id = ? AND occurred_at < ?  "
            + " AND (CAST(? AS text) IS NULL OR owner_id = CAST(? AS text)) GROUP BY subject_id),"
            + " acts AS (   SELECT DISTINCT subject_id, date_trunc('week', occurred_at AT TIME ZONE"
            + " 'UTC') AS aw   FROM analytics_event_all WHERE"
            + SCOPE
            + ")"
            + " SELECT f.cw::date, ((a.aw::date - f.cw::date) / 7), count(*)"
            + " FROM firsts f JOIN acts a ON a.subject_id = f.subject_id"
            + " WHERE f.cw >= (CAST(? AS timestamptz) AT TIME ZONE 'UTC') AND a.aw >= f.cw"
            + " GROUP BY 1, 2 ORDER BY 1, 2",
        rs -> {
          grid.computeIfAbsent(rs.getObject(1, LocalDate.class), w -> new TreeMap<>())
              .put(rs.getInt(2), rs.getLong(3));
        },
        tenant,
        Timestamp.from(until),
        owner,
        owner,
        tenant,
        Timestamp.from(since),
        Timestamp.from(until),
        owner,
        owner,
        Timestamp.from(since));
    LocalDate lastDay = LocalDate.ofInstant(until.minusNanos(1), ZoneOffset.UTC);
    List<Cohort> cohorts = new ArrayList<>();
    grid.forEach(
        (week, byWeek) -> {
          long size = byWeek.getOrDefault(0, 0L);
          if (size < minAudience) {
            return;
          }
          int weeks = (int) (ChronoUnit.DAYS.between(week, lastDay) / 7);
          List<Long> active = new ArrayList<>();
          for (int w = 0; w <= weeks; w++) {
            active.add(byWeek.getOrDefault(w, 0L));
          }
          cohorts.add(Cohort.of(week, size, active));
        });
    return cohorts;
  }

  @Override
  public List<Long> funnel(
      String tenant, Range range, String owner, List<String> kinds, int windowHours) {
    StringBuilder sql = new StringBuilder("WITH s1 AS (SELECT subject_id, min(occurred_at) AS t");
    sql.append(FROM_ANALYTICS_EVENT_ALL_WHERE).append(SCOPE).append(" AND kind = ?");
    sql.append(" GROUP BY subject_id)");
    List<Object> args = new ArrayList<>(Arrays.asList(scope(tenant, range, owner)));
    args.add(kinds.getFirst());
    for (int i = 1; i < kinds.size(); i++) {
      sql.append(", s")
          .append(i + 1)
          .append(" AS (SELECT e.subject_id, min(e.occurred_at) AS t FROM analytics_event_all e")
          .append(" JOIN s")
          .append(i)
          .append(" p ON p.subject_id = e.subject_id WHERE e.tenant_id = ?")
          .append(" AND (CAST(? AS text) IS NULL OR e.owner_id = CAST(? AS text))")
          .append(" AND e.kind = ? AND e.occurred_at > p.t")
          .append(" AND e.occurred_at <= p.t + make_interval(hours => ?)")
          .append(" GROUP BY e.subject_id)");
      args.add(tenant);
      args.add(owner);
      args.add(owner);
      args.add(kinds.get(i));
      args.add(windowHours);
    }
    sql.append(" SELECT ");
    for (int i = 1; i <= kinds.size(); i++) {
      sql.append(i == 1 ? "" : ", ").append("(SELECT count(*) FROM s").append(i).append(')');
    }
    return jdbc.queryForObject(
        sql.toString(),
        (rs, n) -> {
          List<Long> reached = new ArrayList<>();
          for (int i = 1; i <= kinds.size(); i++) {
            reached.add(rs.getLong(i));
          }
          return reached;
        },
        args.toArray());
  }

  @Override
  public List<String> tenants() {
    return jdbc.queryForList("SELECT DISTINCT tenant_id FROM analytics_event", String.class);
  }

  @Override
  public int purge(String tenant, Instant before) {
    return jdbc.update(
        "DELETE FROM analytics_event WHERE tenant_id = ? AND occurred_at < ?",
        tenant,
        Timestamp.from(before));
  }

  private static Object[] scope(String tenant, Range range, String owner) {
    return new Object[] {
      tenant, Timestamp.from(range.from()), Timestamp.from(range.to()), owner, owner
    };
  }

  private static Double rounded(Double value) {
    return value == null ? null : (Math.round(value * 100.0) / 100.0);
  }
}
