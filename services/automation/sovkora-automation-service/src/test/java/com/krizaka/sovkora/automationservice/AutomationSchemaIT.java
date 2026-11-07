package com.krizaka.sovkora.automationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import com.krizaka.test.container.ServiceRoles;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The automation model of ADR-079 against the <b>real</b> schema ({@code
 * V1__automation_baseline.sql}, applied by Flyway as the owner role): the rule set is a row, a tool
 * the catalogue refuses to run ({@code EXEC}) cannot be put to a human, and the audit trail cannot
 * be changed, cut or forged — each event follows the hash of the one before it.
 */
class AutomationSchemaIT {

  private static final String DB = "orazaka_automation_db";
  private static final String ROLE = "orazaka_automation";
  private static final String PASSWORD = "orazaka_automation_pass";
  private static final String ZERO = "0".repeat(64);

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")))
                      .resolve("50-automation.sql")),
              "/docker-entrypoint-initdb.d/50-automation.sql");

  private static HikariDataSource dataSource;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void start() {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    ServiceRoles.assignPassword(POSTGRES, ROLE, PASSWORD);
    org.flywaydb.core.Flyway.configure()
        .dataSource(
            "jdbc:postgresql://%s:%d/%s"
                .formatted(
                    POSTGRES.getHost(),
                    POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                    DB),
            ROLE,
            PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(
        "jdbc:postgresql://"
            + POSTGRES.getHost()
            + ":"
            + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            + "/"
            + DB);
    dataSource.setUsername(ROLE);
    dataSource.setPassword(PASSWORD);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stop() {
    if (dataSource != null) {
      dataSource.close();
    }
    POSTGRES.stop();
  }

  @BeforeEach
  void clean() {
    // audit_event is append-only for everyone, so a test starts from a fresh device instead of a
    // clean table.
    jdbc.update("DELETE FROM automation");
  }

  private UUID device() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO device (id, user_id, name, platform, token_hash) VALUES (?, 'u1', 'MacBook',"
            + " 'macos', 'h')",
        id);
    return id;
  }

  private void event(UUID device, long seq, String prev, String hash) {
    jdbc.update(
        "INSERT INTO audit_event (device_id, seq, kind, prev_hash, hash) VALUES (?, ?,"
            + " 'run.started', ?, ?)",
        device,
        seq,
        prev,
        hash);
  }

  private static String hash(char c) {
    return String.valueOf(c).repeat(64);
  }

  @Test
  @DisplayName(
      "The limits are a versioned rule set: exactly one is active, the newest, and v1 stays seeded")
  void ruleSetIsData() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM automation_settings WHERE active", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM automation_settings WHERE active", Integer.class))
        .isEqualTo(
            jdbc.queryForObject("SELECT max(version) FROM automation_settings", Integer.class));
    assertThat(
            jdbc.queryForObject(
                "SELECT rules->>'approvalTtlHours' FROM automation_settings WHERE version = 1",
                String.class))
        .isEqualTo("24");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO automation_settings (version, rules, active) VALUES (99, '{}',"
                        + " TRUE)"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName(
      "An automation has a trigger, a policy and a run with a trace; deleting it takes them with"
          + " it")
  void automationOwnsItsParts() {
    UUID device = device();
    UUID automation = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation (id, user_id, name, recipe, device_id, settings_version) VALUES (?,"
            + " 'u1', 'Morning PDFs', 'summarise-new-pdfs', ?, 1)",
        automation,
        device);
    jdbc.update(
        "INSERT INTO automation_trigger (automation_id, kind, spec) VALUES (?, 'SCHEDULE',"
            + " '{\"at\":\"07:30\",\"days\":[1,2,3,4,5]}')",
        automation);
    jdbc.update("INSERT INTO automation_policy (automation_id) VALUES (?)", automation);
    UUID run = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation_run (id, automation_id, device_id, device_run_id) VALUES (?, ?, ?,"
            + " 'r-1')",
        run,
        automation,
        device);
    jdbc.update(
        "INSERT INTO run_step (run_id, ordinal, kind, summary) VALUES (?, 1, 'tool', 'read 3"
            + " files')",
        run);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO automation_run (automation_id, device_id, device_run_id) VALUES"
                        + " (?, ?, 'r-1')",
                    automation,
                    device))
        .as("a device pushes a run once, even if it retries")
        .isInstanceOf(DataAccessException.class);

    jdbc.update("DELETE FROM automation WHERE id = ?", automation);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM run_step", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "Code execution is never put to a human: an approval for an EXEC tool is refused by the"
          + " schema")
  void execCannotBeAskedFor() {
    UUID automation = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation (id, user_id, name, recipe, settings_version) VALUES (?, 'u1', 'x',"
            + " 'r', 1)",
        automation);
    UUID run = UUID.randomUUID();
    jdbc.update("INSERT INTO automation_run (id, automation_id) VALUES (?, ?)", run, automation);

    jdbc.update(
        "INSERT INTO approval_request (run_id, server_key, tool, effect, expires_at) VALUES (?,"
            + " 'fs', 'write_file', 'WRITE', now() + interval '1 day')",
        run);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO approval_request (run_id, server_key, tool, effect, expires_at)"
                        + " VALUES (?, 'sh', 'run', 'EXEC', now() + interval '1 day')",
                    run))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("A grant is unique while active; revoking it frees the slot")
  void grantsAreUniqueWhileActive() {
    UUID automation = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation (id, user_id, name, recipe, settings_version) VALUES (?, 'u1', 'x',"
            + " 'r', 1)",
        automation);
    String sql =
        "INSERT INTO tool_grant (automation_id, server_key, tool, scope) VALUES (?, 'fs',"
            + " 'write_file', '/Users/u/Reports')";
    jdbc.update(sql, automation);
    assertThatThrownBy(() -> jdbc.update(sql, automation)).isInstanceOf(DataAccessException.class);
    jdbc.update("UPDATE tool_grant SET revoked_at = now() WHERE automation_id = ?", automation);
    jdbc.update(sql, automation);
  }

  @Test
  @DisplayName(
      "The audit trail is a chain: #1 after the zero hash, then each event after the last one's"
          + " hash")
  void auditChainAcceptsOnlyTheNextLink() {
    UUID device = device();
    event(device, 1, ZERO, hash('a'));
    event(device, 2, hash('a'), hash('b'));
    event(device, 3, hash('b'), hash('c'));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE device_id = ?", Integer.class, device))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("A gap, a repeat, a broken link or a wrong start is refused")
  void auditChainRefusesAnythingElse() {
    UUID device = device();
    assertThatThrownBy(() -> event(device, 2, ZERO, hash('a')))
        .as("must start at #1")
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> event(device, 1, hash('f'), hash('a')))
        .as("must start after the zero hash")
        .isInstanceOf(DataAccessException.class);
    event(device, 1, ZERO, hash('a'));
    assertThatThrownBy(() -> event(device, 3, hash('a'), hash('c')))
        .as("a gap")
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> event(device, 1, ZERO, hash('a')))
        .as("a repeat")
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> event(device, 2, hash('e'), hash('b')))
        .as("a broken link")
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO audit_event (device_id, seq, kind, prev_hash, hash) VALUES (?, 2,"
                        + " 'x', ?, 'not-a-hash')",
                    device,
                    hash('a')))
        .as("a hash is 64 hex characters")
        .isInstanceOf(DataAccessException.class);
    event(device, 2, hash('a'), hash('b'));
  }

  @Test
  @DisplayName("Chains are per device, and an event can be neither changed nor removed")
  void auditIsPerDeviceAndAppendOnly() {
    UUID one = device();
    UUID two = device();
    event(one, 1, ZERO, hash('a'));
    event(two, 1, ZERO, hash('a'));

    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_event SET kind = 'forged' WHERE device_id = ?", one))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE device_id = ?", one))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM device WHERE id = ?", one))
        .as("a device with a trail is revoked, not deleted")
        .isInstanceOf(DataAccessException.class);
  }
}
