package com.krizaka.sovkora.automationservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import com.krizaka.test.container.ServiceRoles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The automation schema is code (krizaka-internal#2255): {@code db/migration} applied by Flyway on
 * a database made by the local bootstrap (50-automation.sql: role, database) creates every table as
 * the owner role; applied a second time it runs nothing and still validates. The container is its
 * own: the shared base class does not expose its database.
 */
class AutomationMigrationsIT {

  private static final String ROLE = "orazaka_automation";
  private static final String PASSWORD = "orazaka_automation_pass";
  private static final String DATABASE = "orazaka_automation_db";

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(initdb().resolve("50-automation.sql")),
              "/docker-entrypoint-initdb.d/50-automation.sql");

  @BeforeAll
  static void start() {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    ServiceRoles.assignPassword(POSTGRES, ROLE, PASSWORD);
  }

  @AfterAll
  static void stop() {
    POSTGRES.stop();
  }

  private static Path initdb() {
    return SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")));
  }

  private static String url(String database) {
    return "jdbc:postgresql://%s:%d/%s"
        .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
  }

  private static Flyway flyway(String database) {
    return Flyway.configure()
        .dataSource(url(database), ROLE, PASSWORD)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .validateOnMigrate(true)
        .cleanDisabled(true)
        .load();
  }

  private static long count(Statement statement, String sql) throws Exception {
    try (ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  @Test
  void anEmptyDatabaseGetsTheWholeSchemaAndASecondRunAppliesNothing() throws Exception {
    assertThat(flyway(DATABASE).migrate().migrationsExecuted)
        .as("first run on the bootstrapped database")
        .isEqualTo(1);
    assertThat(flyway(DATABASE).migrate().migrationsExecuted).as("second run").isZero();
    flyway(DATABASE).validate();

    try (Connection connection = DriverManager.getConnection(url(DATABASE), ROLE, PASSWORD);
        Statement statement = connection.createStatement()) {
      assertThat(
              count(
                  statement,
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                      + " AND table_name <> 'flyway_schema_history'"))
          .as("the context's tables")
          .isEqualTo(16);
      assertThat(count(statement, "SELECT count(*) FROM automation_settings WHERE active"))
          .as("exactly one active rule set")
          .isEqualTo(1);
    }
  }

  @Test
  void theLocalBootstrapCreatesNoTableAndNoRow() throws Exception {
    // One source for the schema: a table in the initdb file would be a second copy, and the one a
    // deployed database never gets.
    String initdb = Files.readString(initdb().resolve("50-automation.sql"));
    assertThat(initdb)
        .doesNotContainIgnoringCase("CREATE TABLE")
        .doesNotContainIgnoringCase("INSERT INTO");
  }
}
