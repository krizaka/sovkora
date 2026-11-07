package com.krizaka.sovkora.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.persistence.infrastructure.config.GovernanceMigrations;
import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The governance schema is code (krizaka-internal#2255): {@code db/governance} applied by Flyway on
 * an empty database creates the four tables and the baseline policies; applied a second time it
 * runs nothing and still validates. The database is shared with other contexts, each with its own
 * history table, so a context that finds the other's tables already there must still apply its V1.
 */
class GovernanceMigrationsIT extends AbstractContainerIntegrationTest {

  private static final long TABLES = 4;

  private static String url(String database) {
    String url = postgres().getJdbcUrl();
    return url.substring(0, url.lastIndexOf('/') + 1) + database;
  }

  private static void createDatabase(String database) throws Exception {
    try (Connection admin =
            DriverManager.getConnection(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
        Statement statement = admin.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + database);
      statement.execute("CREATE DATABASE " + database);
    }
  }

  /** The host service's Flyway, as application.yml configures it for a shared database. */
  private static Flyway host(String database) {
    return Flyway.configure()
        .dataSource(url(database), postgres().getUsername(), postgres().getPassword())
        .locations("classpath:db/other-context")
        .table("flyway_history_other")
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .validateOnMigrate(true)
        .cleanDisabled(true)
        .outOfOrder(false)
        .load();
  }

  private static long count(String database, String sql) throws Exception {
    try (Connection connection =
            DriverManager.getConnection(
                url(database), postgres().getUsername(), postgres().getPassword());
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static long applied(String database, String historyTable) throws Exception {
    return count(
        database,
        "SELECT count(*) FROM "
            + historyTable
            + " WHERE version = '1' AND success AND type = 'SQL'");
  }

  @Test
  void anEmptyDatabaseGetsTheGovernanceSchemaAndASecondRunAppliesNothing() throws Exception {
    String database = "governance_migrations_check";
    createDatabase(database);
    var configuration = host(database).getConfiguration();

    assertThat(GovernanceMigrations.migrate(configuration))
        .as("first run on an empty database")
        .isEqualTo(1);
    assertThat(GovernanceMigrations.migrate(configuration)).as("second run").isZero();
    GovernanceMigrations.validate(configuration);

    assertThat(
            count(
                database,
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                    + " AND table_name NOT LIKE 'flyway_%'"))
        .as("the context's tables")
        .isEqualTo(TABLES);
    assertThat(applied(database, GovernanceMigrations.HISTORY_TABLE)).isEqualTo(1);
    assertThat(count(database, "SELECT count(*) FROM interceptor_policy"))
        .as("the baseline interceptor policies")
        .isEqualTo(9);
    assertThat(
            count(
                database,
                "SELECT count(*) FROM platform_tool_permissions WHERE tool_id = 'run_check'"
                    + " AND required_role = 'ROLE_USER'"))
        .as("the code agent's tool roles, folded from the workspace migration")
        .isEqualTo(1);
  }

  @Test
  void twoContextsMigratingIntoOneDatabaseInSequenceBothApplyTheirV1() throws Exception {
    String database = "governance_shared_check";
    createDatabase(database);

    // The host's own migration first: its tables exist when governance arrives, so without
    // baseline-version 0 the governance history would be baselined at 1 and V1 skipped.
    new GovernanceMigrations().migrate(host(database));

    assertThat(applied(database, "flyway_history_other")).as("the host's V1").isEqualTo(1);
    assertThat(applied(database, GovernanceMigrations.HISTORY_TABLE))
        .as("governance's V1")
        .isEqualTo(1);
    assertThat(count(database, "SELECT count(*) FROM other_context_marker")).isZero();
    assertThat(count(database, "SELECT count(*) FROM interceptor_policy"))
        .as("governance's tables exist and are seeded")
        .isEqualTo(9);

    // A restart: nothing pending in either history.
    new GovernanceMigrations().migrate(host(database));
    assertThat(applied(database, GovernanceMigrations.HISTORY_TABLE)).isEqualTo(1);
    assertThat(
            count(
                database,
                "SELECT count(*) FROM flyway_history_governance WHERE type = 'BASELINE'"
                    + " AND version = '0'"))
        .as("adopted at 0, never at 1, so V1 ran")
        .isEqualTo(1);
  }

  @Test
  void theLocalBootstrapCreatesNothingAndKeepsNoSchema() throws Exception {
    // One source for the schema: a table in the initdb file would be a second copy, and the one a
    // deployed database never gets.
    String initdb =
        Files.readString(
            SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")))
                .resolve("60-governance.sql"));
    assertThat(initdb)
        .doesNotContainIgnoringCase("CREATE TABLE")
        .doesNotContainIgnoringCase("INSERT INTO");
  }
}
