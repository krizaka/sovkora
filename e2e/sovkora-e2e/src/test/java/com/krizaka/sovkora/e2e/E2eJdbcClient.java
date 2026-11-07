package com.krizaka.sovkora.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live JDBC infrastructure accessor for E2E database assertions.
 *
 * <p>Connects directly to the provisioned Postgres container via system properties injected by
 * Failsafe. No mocking, no Spring context — raw JDBC against the live database instance.
 */
final class E2eJdbcClient {

  private static final String DB_URL = System.getProperty("db.url");
  private static final String DB_USER = System.getProperty("db.username");
  private static final String DB_PASS = System.getProperty("db.password");
  private static final String IDENTITY_DB_URL = System.getProperty("identity.db.url");
  private static final String IDENTITY_DB_USER = System.getProperty("identity.db.username");
  private static final String IDENTITY_DB_PASS = System.getProperty("identity.db.password");

  private E2eJdbcClient() {}

  /** Opens a live JDBC connection to the app database (jobs, chat, config plane). */
  static Connection connect() throws SQLException {
    if (DB_URL == null || DB_URL.isBlank()) {
      throw new IllegalStateException(
          "db.url system property is missing. Ensure DB_LOCAL_* vars are set in .env.");
    }
    return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
  }

  /** Opens a live JDBC connection to the identity service's own database (Phase 2 split). */
  static Connection connectIdentity() throws SQLException {
    if (IDENTITY_DB_URL == null || IDENTITY_DB_URL.isBlank()) {
      throw new IllegalStateException(
          "identity.db.url system property is missing. Ensure IDENTITY_DB_* vars are set in .env.");
    }
    return DriverManager.getConnection(IDENTITY_DB_URL, IDENTITY_DB_USER, IDENTITY_DB_PASS);
  }

  /**
   * Executes a parameterized SELECT and returns the first row as a Map. Returns null if no rows are
   * found.
   */
  static Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
    return queryOne(connect(), sql, params);
  }

  /** {@link #queryOne} against the identity database (users, profiles, credentials). */
  static Map<String, Object> queryOneIdentity(String sql, Object... params) throws SQLException {
    return queryOne(connectIdentity(), sql, params);
  }

  private static Map<String, Object> queryOne(Connection connection, String sql, Object... params)
      throws SQLException {
    try (Connection conn = connection;
        PreparedStatement ps = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return null;
        int cols = rs.getMetaData().getColumnCount();
        Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= cols; i++) {
          row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
        }
        return row;
      }
    }
  }

  /** Executes a parameterized SELECT and returns all rows. */
  static List<Map<String, Object>> queryAll(String sql, Object... params) throws SQLException {
    try (Connection conn = connect();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int cols = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Map<String, Object> row = new HashMap<>();
          for (int i = 1; i <= cols; i++) {
            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
          }
          rows.add(row);
        }
        return rows;
      }
    }
  }

  /** Returns the count of rows matching the query. */
  static long count(String sql, Object... params) throws SQLException {
    return count(connect(), sql, params);
  }

  /** {@link #count} against the identity database. */
  static long countIdentity(String sql, Object... params) throws SQLException {
    return count(connectIdentity(), sql, params);
  }

  private static long count(Connection connection, String sql, Object... params)
      throws SQLException {
    try (Connection conn = connection;
        PreparedStatement ps = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0;
      }
    }
  }

  /**
   * Executes a parameterized DML statement (INSERT, UPDATE, DELETE).
   *
   * @return number of affected rows.
   */
  static int execute(String sql, Object... params) throws SQLException {
    try (Connection conn = connect();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      return ps.executeUpdate();
    }
  }
}
