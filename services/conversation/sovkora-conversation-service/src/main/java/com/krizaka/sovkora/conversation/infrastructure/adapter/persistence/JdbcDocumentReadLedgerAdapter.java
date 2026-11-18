package com.krizaka.sovkora.conversation.infrastructure.adapter.persistence;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentReading;
import com.krizaka.sovkora.conversation.domain.model.reader.PageKind;
import com.krizaka.sovkora.conversation.domain.port.DocumentReadLedger;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One line of {@code document_read_measurement} per page read: appended, never rewritten (the
 * table's trigger refuses an update). It holds the caller's opaque ids, the reader, its version,
 * the cost and the latency; no word of the page and not its hash.
 */
@Component
public class JdbcDocumentReadLedgerAdapter implements DocumentReadLedger {

  private static final String APPEND =
      "INSERT INTO document_read_measurement (tenant_id, actor_id, page_kind, reader,"
          + " reader_version, cost_micros, latency_ms, fallback_from)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private final JdbcTemplate jdbcTemplate;

  public JdbcDocumentReadLedgerAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void record(Actor actor, PageKind kind, DocumentReading reading) {
    jdbcTemplate.update(
        APPEND,
        actor.tenant(),
        actor.subject(),
        kind.wire(),
        reading.reader(),
        reading.version(),
        reading.costMicros(),
        reading.latencyMs(),
        reading.fallbackFrom());
  }
}
