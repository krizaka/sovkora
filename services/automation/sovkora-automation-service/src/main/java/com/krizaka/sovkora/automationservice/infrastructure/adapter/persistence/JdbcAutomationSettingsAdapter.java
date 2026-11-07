package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.automationservice.domain.port.AutomationSettingsRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The active rule set: one JSONB document of {@code automation_settings} (ADR-079 §1). */
@Component
class JdbcAutomationSettingsAdapter implements AutomationSettingsRepository {

  private final JdbcTemplate jdbc;

  JdbcAutomationSettingsAdapter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "JdbcTemplate cannot be null");
  }

  @Override
  public Optional<Integer> intValue(String key) {
    return jdbc
        .query(
            "SELECT rules ->> ? FROM automation_settings WHERE active",
            (rs, rowNum) -> Optional.ofNullable(rs.getString(1)),
            key)
        .stream()
        .flatMap(Optional::stream)
        .findFirst()
        .flatMap(JdbcAutomationSettingsAdapter::parse);
  }

  private static Optional<Integer> parse(String value) {
    try {
      return Optional.of(Integer.parseInt(value.strip()));
    } catch (NumberFormatException unreadable) {
      return Optional.empty();
    }
  }
}
