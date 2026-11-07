package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.model.Automation;
import com.krizaka.sovkora.automationservice.domain.model.AutomationChange;
import com.krizaka.sovkora.automationservice.domain.model.NewAutomation;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Automations on {@code automation}; every statement carries the owner. */
@Component
class JdbcAutomationRepositoryAdapter implements AutomationRepository {

  /** A cloud automation's recipe is {@code studio:<studioKey>} (ADR-079 §1, ADR-084). */
  private static final String RECIPE_PREFIX = "studio:";

  private static final String COLUMNS =
      "id, user_id, name, recipe, inputs::text AS inputs, enabled, created_at, updated_at";
  private static final String CLOUD = " runs_where = 'CLOUD'";
  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcAutomationRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  @Override
  public int count(String ownerId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM automation WHERE user_id = ? AND" + CLOUD,
            Integer.class,
            ownerId);
    return n == null ? 0 : n;
  }

  @Override
  public Automation create(String ownerId, NewAutomation draft) {
    return jdbc.queryForObject(
        "INSERT INTO automation (user_id, name, recipe, runs_where, inputs, enabled,"
            + " settings_version) VALUES (?, ?, ?, 'CLOUD', ?::jsonb, ?,"
            + " (SELECT version FROM automation_settings WHERE active)) RETURNING "
            + COLUMNS,
        (rs, rowNum) -> automation(rs),
        ownerId,
        draft.name(),
        RECIPE_PREFIX + draft.studioKey(),
        json.writeValueAsString(draft.inputs()),
        draft.enabled());
  }

  @Override
  public List<Automation> list(String ownerId) {
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM automation WHERE user_id = ? AND"
            + CLOUD
            + " ORDER BY created_at, id",
        (rs, rowNum) -> automation(rs),
        ownerId);
  }

  @Override
  public Optional<Automation> find(String ownerId, UUID id) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + " FROM automation WHERE user_id = ? AND id = ? AND" + CLOUD,
            (rs, rowNum) -> automation(rs),
            ownerId,
            id)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Automation> update(String ownerId, UUID id, AutomationChange change) {
    return jdbc
        .query(
            "UPDATE automation SET name = COALESCE(?::text, name),"
                + " inputs = COALESCE(?::jsonb, inputs), enabled = COALESCE(?::boolean, enabled),"
                + " updated_at = now() WHERE user_id = ? AND id = ? AND"
                + CLOUD
                + " RETURNING "
                + COLUMNS,
            (rs, rowNum) -> automation(rs),
            change.name(),
            change.inputs() == null ? null : json.writeValueAsString(change.inputs()),
            change.enabled(),
            ownerId,
            id)
        .stream()
        .findFirst();
  }

  @Override
  public boolean delete(String ownerId, UUID id) {
    return jdbc.update(
            "DELETE FROM automation WHERE user_id = ? AND id = ? AND" + CLOUD, ownerId, id)
        > 0;
  }

  private static String studioKey(String recipe) {
    return recipe.startsWith(RECIPE_PREFIX) ? recipe.substring(RECIPE_PREFIX.length()) : recipe;
  }

  private Automation automation(ResultSet rs) throws SQLException {
    return new Automation(
        rs.getObject("id", UUID.class),
        rs.getString("user_id"),
        rs.getString("name"),
        studioKey(rs.getString("recipe")),
        json.readValue(rs.getString("inputs"), OBJECT),
        rs.getBoolean("enabled"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
