package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.persistence;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.CatalogueItem;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.CreatorInsights;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Interaction;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.ProfileSignal;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Vector;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The recommender's store on {@code orazaka_knowledge_db} (pgvector). Parameterised SQL only;
 * vectors travel as pgvector text literals ({@code ?::vector}), arrays as text-array literals
 * ({@code ?::text[]}, {@link PgArrays}). Every statement is scoped by {@code tenant_id}.
 */
@Repository
class JdbcRecommendationRepositoryAdapter implements RecommendationRepository {

  /** Peers considered for co-engagement: enough to be useful, bounded to keep the query cheap. */
  private static final int PEERS = 500;

  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  JdbcRecommendationRepositoryAdapter(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.json = Objects.requireNonNull(json, "json");
  }

  @Override
  public Optional<RecommendationSettings> settings(String tenant) {
    return jdbc
        .query(
            "SELECT version, settings::text FROM reco_settings WHERE tenant_id IN (?, '*')"
                + " ORDER BY (tenant_id = '*'), version DESC LIMIT 1",
            (rs, n) ->
                json.readValue(rs.getString(2), SettingsDocument.class).toSettings(rs.getInt(1)),
            tenant)
        .stream()
        .findFirst();
  }

  @Override
  public int insertEvents(String tenant, List<Interaction> events) {
    int[][] counts =
        jdbc.batchUpdate(
            "INSERT INTO reco_event (tenant_id, event_id, user_id, item_id, creator_id, kind,"
                + " watched_pct, occurred_at) VALUES (?, ?, ?, ?, ?, ?::reco_event_kind, ?, ?)"
                + " ON CONFLICT (tenant_id, event_id) DO NOTHING",
            events,
            100,
            (ps, e) -> {
              ps.setString(1, tenant);
              ps.setString(2, e.eventId());
              ps.setString(3, e.userId());
              ps.setString(4, e.itemId());
              ps.setString(5, e.creatorId());
              ps.setString(6, e.kind().name());
              if (e.watchedPct() == null) {
                ps.setNull(7, java.sql.Types.SMALLINT);
              } else {
                ps.setShort(7, e.watchedPct().shortValue());
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
  public List<ProfileSignal> profileSignals(String tenant, String userId, int limit) {
    return jdbc.query(
        "SELECT e.kind::text, e.occurred_at, e.watched_pct, i.embedding::text FROM reco_event e"
            + " JOIN reco_item i ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id"
            + " WHERE e.tenant_id = ? AND e.user_id = ? AND i.embedding IS NOT NULL"
            + " ORDER BY e.occurred_at DESC LIMIT ?",
        (rs, n) ->
            new ProfileSignal(
                InteractionKind.valueOf(rs.getString(1)),
                rs.getTimestamp(2).toInstant(),
                (Integer) rs.getObject(3, Integer.class),
                Vector.parse(rs.getString(4))),
        tenant,
        userId,
        limit);
  }

  @Override
  public void saveProfile(String tenant, String userId, UserProfile profile, Instant at) {
    jdbc.update(
        "INSERT INTO reco_user_profile (tenant_id, user_id, embedding, interactions, updated_at)"
            + " VALUES (?, ?, ?::vector, ?, ?) ON CONFLICT (tenant_id, user_id) DO UPDATE SET"
            + " embedding = EXCLUDED.embedding, interactions = EXCLUDED.interactions,"
            + " updated_at = EXCLUDED.updated_at",
        tenant,
        userId,
        profile.vector().literal(),
        profile.interactions(),
        Timestamp.from(at));
  }

  @Override
  public void deleteProfile(String tenant, String userId) {
    jdbc.update(
        "DELETE FROM reco_user_profile WHERE tenant_id = ? AND user_id = ?", tenant, userId);
  }

  @Override
  public Optional<UserProfile> profile(String tenant, String userId) {
    return jdbc
        .query(
            "SELECT embedding::text, interactions FROM reco_user_profile"
                + " WHERE tenant_id = ? AND user_id = ?",
            (rs, n) -> new UserProfile(Vector.parse(rs.getString(1)), rs.getInt(2)),
            tenant,
            userId)
        .stream()
        .findFirst();
  }

  @Override
  public Map<String, String> contentHashes(String tenant, Collection<String> itemIds) {
    Map<String, String> hashes = new LinkedHashMap<>();
    if (itemIds.isEmpty()) {
      return hashes;
    }
    jdbc.query(
        "SELECT item_id, content_hash FROM reco_item WHERE tenant_id = ?"
            + " AND item_id = ANY(?::text[]) AND embedding IS NOT NULL",
        rs -> {
          hashes.put(rs.getString(1), rs.getString(2));
        },
        tenant,
        PgArrays.text(itemIds));
    return hashes;
  }

  @Override
  public void upsertItem(
      String tenant, CatalogueItem item, String model, Vector vector, Instant at) {
    if (vector != null) {
      jdbc.update(
          "INSERT INTO reco_item (tenant_id, item_id, creator_id, tags, language, duration_seconds,"
              + " published_at, content_hash, model, embedding, updated_at)"
              + " VALUES (?, ?, ?, ?::text[], ?, ?, ?, ?, ?, ?::vector, ?)"
              + " ON CONFLICT (tenant_id, item_id) DO UPDATE SET creator_id = EXCLUDED.creator_id,"
              + " tags = EXCLUDED.tags, language = EXCLUDED.language,"
              + " duration_seconds = EXCLUDED.duration_seconds, published_at = EXCLUDED.published_at,"
              + " content_hash = EXCLUDED.content_hash, model = EXCLUDED.model,"
              + " embedding = EXCLUDED.embedding, updated_at = EXCLUDED.updated_at",
          tenant,
          item.itemId(),
          item.creatorId(),
          PgArrays.text(item.tags()),
          item.language(),
          item.durationSeconds(),
          Timestamp.from(item.publishedAt()),
          item.contentHash(),
          model,
          vector.literal(),
          Timestamp.from(at));
    } else {
      jdbc.update(
          "UPDATE reco_item SET creator_id = ?, tags = ?::text[], language = ?,"
              + " duration_seconds = ?, published_at = ?, updated_at = ?"
              + " WHERE tenant_id = ? AND item_id = ?",
          item.creatorId(),
          PgArrays.text(item.tags()),
          item.language(),
          item.durationSeconds(),
          Timestamp.from(item.publishedAt()),
          Timestamp.from(at),
          tenant,
          item.itemId());
    }
  }

  @Override
  public int eraseUsers(String tenant, Collection<String> userIds) {
    String ids = PgArrays.text(userIds);
    jdbc.update(
        "DELETE FROM reco_user_profile WHERE tenant_id = ? AND user_id = ANY(?::text[])",
        tenant,
        ids);
    jdbc.update(
        "UPDATE reco_request SET user_id = NULL WHERE tenant_id = ? AND user_id = ANY(?::text[])",
        tenant,
        ids);
    return jdbc.update(
        "DELETE FROM reco_event WHERE tenant_id = ? AND user_id = ANY(?::text[])", tenant, ids);
  }

  @Override
  public int eraseItems(String tenant, Collection<String> itemIds) {
    String ids = PgArrays.text(itemIds);
    jdbc.update(
        "DELETE FROM reco_event WHERE tenant_id = ? AND item_id = ANY(?::text[])", tenant, ids);
    return jdbc.update(
        "DELETE FROM reco_item WHERE tenant_id = ? AND item_id = ANY(?::text[])", tenant, ids);
  }

  @Override
  public Set<String> engagedItems(String tenant, String userId) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT DISTINCT item_id FROM reco_event WHERE tenant_id = ? AND user_id = ?"
                + " AND item_id IS NOT NULL",
            String.class,
            tenant,
            userId));
  }

  @Override
  public Set<String> reportedCreators(String tenant, String userId) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT DISTINCT i.creator_id FROM reco_event e JOIN reco_item i"
                + " ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id"
                + " WHERE e.tenant_id = ? AND e.user_id = ? AND e.kind = 'REPORT'",
            String.class,
            tenant,
            userId));
  }

  @Override
  public Map<String, Double> nearest(String tenant, Vector vector, int limit) {
    Map<String, Double> out = new LinkedHashMap<>();
    String literal = vector.literal();
    jdbc.query(
        "SELECT item_id, 1 - (embedding <=> ?::vector) FROM reco_item"
            + " WHERE tenant_id = ? AND embedding IS NOT NULL"
            + " ORDER BY embedding <=> ?::vector LIMIT ?",
        rs -> {
          out.put(rs.getString(1), rs.getDouble(2));
        },
        literal,
        tenant,
        literal,
        limit);
    return out;
  }

  @Override
  public Map<String, Double> coEngaged(
      String tenant, String userId, List<InteractionKind> positive, int limit) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (positive.isEmpty()) {
      return out;
    }
    String kinds = PgArrays.text(positive.stream().map(Enum::name).toList());
    jdbc.query(
        "WITH mine AS (SELECT DISTINCT item_id FROM reco_event WHERE tenant_id = ? AND user_id = ?"
            + "   AND item_id IS NOT NULL AND kind = ANY(?::reco_event_kind[])),"
            + " peers AS (SELECT DISTINCT e.user_id FROM reco_event e JOIN mine m ON m.item_id = e.item_id"
            + "   WHERE e.tenant_id = ? AND e.user_id <> ? AND e.kind = ANY(?::reco_event_kind[]) LIMIT ?)"
            + " SELECT e.item_id, count(DISTINCT e.user_id) FROM reco_event e"
            + " JOIN peers p ON p.user_id = e.user_id"
            + " WHERE e.tenant_id = ? AND e.item_id IS NOT NULL AND e.kind = ANY(?::reco_event_kind[])"
            + "   AND e.item_id NOT IN (SELECT item_id FROM mine)"
            + " GROUP BY e.item_id ORDER BY count(DISTINCT e.user_id) DESC, e.item_id LIMIT ?",
        rs -> {
          out.put(rs.getString(1), rs.getDouble(2));
        },
        tenant,
        userId,
        kinds,
        tenant,
        userId,
        kinds,
        PEERS,
        tenant,
        kinds,
        limit);
    return out;
  }

  @Override
  public List<String> freshest(String tenant, int limit) {
    return jdbc.queryForList(
        "SELECT item_id FROM reco_item WHERE tenant_id = ? ORDER BY published_at DESC, item_id"
            + " LIMIT ?",
        String.class,
        tenant,
        limit);
  }

  @Override
  public Set<String> followedCreators(String tenant, String userId) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT creator_id FROM (SELECT DISTINCT ON (creator_id) creator_id, kind"
                + " FROM reco_event WHERE tenant_id = ? AND user_id = ?"
                + " AND kind IN ('FOLLOW', 'UNFOLLOW')"
                + " ORDER BY creator_id, occurred_at DESC) latest WHERE kind = 'FOLLOW'",
            String.class,
            tenant,
            userId));
  }

  @Override
  public List<String> itemsOfCreators(String tenant, Collection<String> creatorIds, int limit) {
    if (creatorIds.isEmpty()) {
      return List.of();
    }
    return jdbc.queryForList(
        "SELECT item_id FROM reco_item WHERE tenant_id = ? AND creator_id = ANY(?::text[])"
            + " ORDER BY published_at DESC, item_id LIMIT ?",
        String.class,
        tenant,
        PgArrays.text(creatorIds),
        limit);
  }

  @Override
  public Map<String, Map<InteractionKind, Long>> recentEngagement(
      String tenant, Instant since, List<InteractionKind> kinds, int limit) {
    Map<String, Map<InteractionKind, Long>> out = new LinkedHashMap<>();
    if (kinds.isEmpty()) {
      return out;
    }
    jdbc.query(
        "WITH top AS (SELECT item_id FROM reco_event WHERE tenant_id = ? AND occurred_at > ?"
            + "   AND item_id IS NOT NULL AND kind = ANY(?::reco_event_kind[])"
            + "   GROUP BY item_id ORDER BY count(*) DESC, item_id LIMIT ?)"
            + " SELECT e.item_id, e.kind::text, count(*) FROM reco_event e JOIN top t ON t.item_id = e.item_id"
            + " WHERE e.tenant_id = ? AND e.occurred_at > ? AND e.kind = ANY(?::reco_event_kind[])"
            + " GROUP BY e.item_id, e.kind",
        rs -> {
          out.computeIfAbsent(rs.getString(1), k -> new EnumMap<>(InteractionKind.class))
              .put(InteractionKind.valueOf(rs.getString(2)), rs.getLong(3));
        },
        tenant,
        Timestamp.from(since),
        PgArrays.text(kinds.stream().map(Enum::name).toList()),
        limit,
        tenant,
        Timestamp.from(since),
        PgArrays.text(kinds.stream().map(Enum::name).toList()));
    return out;
  }

  @Override
  public List<ItemFacts> itemFacts(String tenant, Collection<String> itemIds) {
    if (itemIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        "SELECT item_id, creator_id, published_at, embedding::text FROM reco_item"
            + " WHERE tenant_id = ? AND item_id = ANY(?::text[])",
        (rs, n) -> facts(rs),
        tenant,
        PgArrays.text(itemIds));
  }

  @Override
  public Optional<ItemFacts> item(String tenant, String itemId) {
    return jdbc
        .query(
            "SELECT item_id, creator_id, published_at, embedding::text FROM reco_item"
                + " WHERE tenant_id = ? AND item_id = ?",
            (rs, n) -> facts(rs),
            tenant,
            itemId)
        .stream()
        .findFirst();
  }

  private static ItemFacts facts(ResultSet rs) throws SQLException {
    String embedding = rs.getString(4);
    return new ItemFacts(
        rs.getString(1),
        rs.getString(2),
        rs.getTimestamp(3).toInstant(),
        embedding == null ? null : Vector.parse(embedding));
  }

  @Override
  public void logRequest(
      UUID requestId,
      String tenant,
      String userId,
      String kind,
      String strategy,
      List<String> itemIds,
      Instant at) {
    jdbc.update(
        "INSERT INTO reco_request (request_id, tenant_id, user_id, kind, strategy, item_ids,"
            + " created_at) VALUES (?, ?, ?, ?, ?, ?::text[], ?)",
        requestId,
        tenant,
        userId,
        kind,
        strategy,
        PgArrays.text(itemIds),
        Timestamp.from(at));
  }

  @Override
  public CreatorInsights insights(
      String tenant, String creatorId, List<InteractionKind> positive, int minAudience) {
    String kinds = PgArrays.text(positive.stream().map(Enum::name).toList());
    List<CreatorInsights.ItemPerformance> items =
        jdbc.query(
            "SELECT i.item_id,"
                + " count(e.event_id) FILTER (WHERE e.kind = 'VIEW'),"
                + " count(e.event_id) FILTER (WHERE e.kind = ANY(?::reco_event_kind[]) AND e.kind <> 'VIEW'),"
                + " count(e.event_id) FILTER (WHERE e.kind = 'SKIP'),"
                + " avg(e.watched_pct) FILTER (WHERE e.kind = 'VIEW' AND e.watched_pct IS NOT NULL)"
                + " FROM reco_item i LEFT JOIN reco_event e"
                + "   ON e.tenant_id = i.tenant_id AND e.item_id = i.item_id"
                + " WHERE i.tenant_id = ? AND i.creator_id = ?"
                + " GROUP BY i.item_id ORDER BY 2 DESC, i.item_id LIMIT 50",
            (rs, n) -> {
              long views = rs.getLong(2);
              long engagements = rs.getLong(3);
              Object avg = rs.getObject(5);
              return new CreatorInsights.ItemPerformance(
                  rs.getString(1),
                  views,
                  engagements,
                  rs.getLong(4),
                  rate(engagements, views),
                  avg == null ? null : Math.round(rs.getDouble(5) * 10) / 10.0);
            },
            kinds,
            tenant,
            creatorId);
    List<CreatorInsights.TagPerformance> tags =
        jdbc.query(
            "SELECT t.tag, count(DISTINCT i.item_id),"
                + " count(e.event_id) FILTER (WHERE e.kind = 'VIEW'),"
                + " count(e.event_id) FILTER (WHERE e.kind = ANY(?::reco_event_kind[]) AND e.kind <> 'VIEW')"
                + " FROM reco_item i CROSS JOIN LATERAL unnest(i.tags) AS t(tag)"
                + " LEFT JOIN reco_event e ON e.tenant_id = i.tenant_id AND e.item_id = i.item_id"
                + " WHERE i.tenant_id = ? AND i.creator_id = ?"
                + " GROUP BY t.tag ORDER BY 4 DESC, 3 DESC, t.tag LIMIT 10",
            (rs, n) ->
                new CreatorInsights.TagPerformance(
                    rs.getString(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getLong(4),
                    rate(rs.getLong(4), rs.getLong(3))),
            kinds,
            tenant,
            creatorId);
    List<CreatorInsights.HourOfWeek> hours =
        jdbc.query(
            "SELECT extract(isodow FROM e.occurred_at AT TIME ZONE 'UTC')::int,"
                + " extract(hour FROM e.occurred_at AT TIME ZONE 'UTC')::int, count(*)"
                + " FROM reco_event e JOIN reco_item i ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id"
                + " WHERE e.tenant_id = ? AND i.creator_id = ? AND e.kind = ANY(?::reco_event_kind[])"
                + " GROUP BY 1, 2 HAVING count(DISTINCT e.user_id) >= ?"
                + " ORDER BY 3 DESC, 1, 2 LIMIT 3",
            (rs, n) -> new CreatorInsights.HourOfWeek(rs.getInt(1), rs.getInt(2), rs.getLong(3)),
            tenant,
            creatorId,
            kinds,
            minAudience);
    List<CreatorInsights.AlsoWatched> also =
        jdbc.query(
            "WITH audience AS (SELECT DISTINCT e.user_id FROM reco_event e JOIN reco_item i"
                + "   ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id"
                + "   WHERE e.tenant_id = ? AND i.creator_id = ? AND e.kind = ANY(?::reco_event_kind[]))"
                + " SELECT i.creator_id, count(DISTINCT e.user_id) FROM reco_event e"
                + " JOIN audience a ON a.user_id = e.user_id"
                + " JOIN reco_item i ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id"
                + " WHERE e.tenant_id = ? AND i.creator_id <> ? AND e.kind = ANY(?::reco_event_kind[])"
                + " GROUP BY i.creator_id HAVING count(DISTINCT e.user_id) >= ?"
                + " ORDER BY 2 DESC, 1 LIMIT 5",
            (rs, n) -> new CreatorInsights.AlsoWatched(rs.getString(1), rs.getLong(2)),
            tenant,
            creatorId,
            kinds,
            tenant,
            creatorId,
            kinds,
            minAudience);
    return new CreatorInsights(creatorId, minAudience, items, tags, hours, also);
  }

  private static double rate(long part, long whole) {
    return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 1000.0;
  }

  @Override
  public List<String> tenants() {
    return jdbc.queryForList(
        "SELECT tenant_id FROM reco_event UNION SELECT tenant_id FROM reco_request", String.class);
  }

  @Override
  public int purgeEvents(String tenant, Instant before) {
    return jdbc.update(
        "DELETE FROM reco_event WHERE tenant_id = ? AND occurred_at < ?",
        tenant,
        Timestamp.from(before));
  }

  @Override
  public int purgeRequests(String tenant, Instant before) {
    return jdbc.update(
        "DELETE FROM reco_request WHERE tenant_id = ? AND created_at < ?",
        tenant,
        Timestamp.from(before));
  }
}
