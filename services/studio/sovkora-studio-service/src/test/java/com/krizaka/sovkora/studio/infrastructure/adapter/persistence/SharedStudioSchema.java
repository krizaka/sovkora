package com.krizaka.sovkora.studio.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.studio.StudioSchema;
import com.krizaka.sovkora.test.support.architecture.SqlBoundaryRules;
import com.krizaka.test.container.ServiceRoles;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Builds the Studio schema in the shared container of {@code AbstractContainerIntegrationTest}
 * once: {@code 80-studio.sql} creates the role and {@code orazaka_studio_db}, then Flyway applies
 * {@code db/migration}; a second class asking for it must find it, not build it again.
 */
public final class SharedStudioSchema {

  public static final String STUDIO_DB = "orazaka_studio_db";
  private static final String STUDIO_ROLE = "orazaka_studio";
  private static final String STUDIO_PASSWORD = "orazaka_studio_pass";

  private SharedStudioSchema() {}

  /**
   * @param postgres the shared container
   * @throws Exception when the container cannot be driven
   */
  public static synchronized void ensureLoaded(PostgreSQLContainer<?> postgres) throws Exception {
    var exists =
        postgres.execInContainer(
            "psql",
            "-U",
            postgres.getUsername(),
            "-d",
            postgres.getDatabaseName(),
            "-tAc",
            "SELECT 1 FROM pg_database WHERE datname = '" + STUDIO_DB + "'");
    if ("1".equals(exists.getStdout().trim())) {
      return;
    }
    postgres.copyFileToContainer(
        MountableFile.forHostPath(
            SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")))
                .resolve("80-studio.sql")),
        "/tmp/80-studio.sql");
    var created =
        postgres.execInContainer(
            "psql",
            "-U",
            postgres.getUsername(),
            "-d",
            postgres.getDatabaseName(),
            "-v",
            "ON_ERROR_STOP=1",
            "-f",
            "/tmp/80-studio.sql");
    assertThat(created.getExitCode()).as(created.getStderr()).isZero();
    ServiceRoles.assignPassword(postgres, STUDIO_ROLE, STUDIO_PASSWORD);
    StudioSchema.migrate(postgres, STUDIO_ROLE, STUDIO_PASSWORD);
  }

  /**
   * The Studio database of the shared container, its schema loaded, as the container's owner.
   *
   * @param postgres the shared container
   * @return a pool the caller closes
   * @throws Exception when the container cannot be driven
   */
  public static HikariDataSource dataSource(PostgreSQLContainer<?> postgres) throws Exception {
    ensureLoaded(postgres);
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(
        postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + STUDIO_DB));
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return dataSource;
  }
}
