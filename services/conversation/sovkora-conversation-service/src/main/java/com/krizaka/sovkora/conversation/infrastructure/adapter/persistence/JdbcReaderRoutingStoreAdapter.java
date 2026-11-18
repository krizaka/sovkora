package com.krizaka.sovkora.conversation.infrastructure.adapter.persistence;

import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;
import com.krizaka.sovkora.conversation.domain.port.ReaderRoutingStore;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The routing in {@code document_reader_setting} (Flyway V2), read at every call and never cached:
 * an operator's change of a row is the next call's routing. The table holds a few rows by
 * construction (one per routing key); the read is bounded all the same.
 */
@Component
public class JdbcReaderRoutingStoreAdapter implements ReaderRoutingStore {

  private static final String ROWS =
      "SELECT setting_key, setting_value FROM document_reader_setting ORDER BY setting_key"
          + " LIMIT 200";

  private final JdbcTemplate jdbcTemplate;

  public JdbcReaderRoutingStoreAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ReaderRouting current() {
    Map<String, String> settings = new HashMap<>();
    jdbcTemplate.query(
        ROWS,
        rs -> {
          settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
        });
    return new ReaderRouting(settings);
  }
}
