package com.krizaka.sovkora.test.support.leak;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;

/**
 * Looks for a mark, a unique string a test planted in a document, in what a service left behind
 * (krizaka-internal#3376): every table of a database, and every log line written while the probe
 * was open.
 *
 * <p>Used with {@code try (LeakProbe probe = LeakProbe.open()) { … }}: logging is captured until
 * the block ends, then {@link #logLinesHolding} and {@link #tablesHolding} answer.
 */
public final class LeakProbe implements AutoCloseable {

  private final Logger root;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private LeakProbe() {
    root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    appender.start();
    root.addAppender(appender);
  }

  /** Starts capturing every log line. */
  public static LeakProbe open() {
    return new LeakProbe();
  }

  @Override
  public void close() {
    root.detachAppender(appender);
  }

  /** The captured log lines (message and the message of any attached exception) with the mark. */
  public List<String> logLinesHolding(String mark) {
    return appender.list.stream()
        .map(
            event ->
                event.getFormattedMessage()
                    + (event.getThrowableProxy() == null
                        ? ""
                        : " " + event.getThrowableProxy().getMessage()))
        .filter(line -> line.contains(mark))
        .toList();
  }

  /**
   * The tables of the {@code public} schema with a row that holds the text in any column: each row
   * is read as JSON, so text and JSON columns alike are searched.
   */
  public static List<String> tablesHolding(DataSource dataSource, String text) throws SQLException {
    List<String> hits = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement scan = connection.prepareStatement(TABLES_HOLDING)) {
      scan.setString(1, "%" + text + "%");
      try (ResultSet rows = scan.executeQuery()) {
        while (rows.next()) {
          hits.add(rows.getString(1));
        }
      }
    }
    return hits;
  }

  /** One statement: the table names are quoted by the database ({@code %I}), never by this code. */
  private static final String TABLES_HOLDING =
      "SELECT table_name FROM information_schema.tables"
          + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
          + " AND (xpath('/row/c/text()', query_to_xml(format("
          + "'SELECT count(*) AS c FROM %I t WHERE row_to_json(t)::text LIKE %L', table_name, ?),"
          + " false, true, '')))[1]::text::int > 0 ORDER BY 1";
}
