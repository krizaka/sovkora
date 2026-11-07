package com.krizaka.sovkora.persistence.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * Applies the governance schema (interceptor policies, their predicates and version history, tool
 * permissions) in the shared {@code orazaka_db}, next to the host service's own Flyway migration
 * (krizaka-internal#2255).
 *
 * <p>The database is shared by three contexts (conversation, job, governance), one Flyway history
 * table each; this context's is {@value #HISTORY_TABLE} and its files are {@value #LOCATION},
 * outside {@code db/migration} so the host's own scan never sees them (two {@code V1} would
 * collide). It runs as the host's {@link FlywayMigrationStrategy}, hence inside the database
 * initialisation that everything using the database waits for, with the host's migrator credentials
 * and settings.
 *
 * <p>Baselining at {@code 0}: on a fresh database where another context already created its tables,
 * {@code baseline-on-migrate} at the default version 1 would mark this context's {@code V1} applied
 * and skip it.
 */
public final class GovernanceMigrations implements FlywayMigrationStrategy {

  /** Where the governance files are. */
  public static final String LOCATION = "classpath:db/governance";

  /** This context's history table in the shared database. */
  public static final String HISTORY_TABLE = "flyway_history_governance";

  /** Property a host sets to {@code true} to apply the governance schema at startup. */
  public static final String ENABLED_PROPERTY = "orazaka.governance.migrations.enabled";

  @Override
  public void migrate(Flyway host) {
    host.migrate();
    migrate(host.getConfiguration());
  }

  /**
   * Migrates the governance schema with the connection and the settings of {@code host}.
   *
   * @param host the host service's Flyway configuration (credentials, validation flags, baseline)
   * @return the number of migrations executed
   */
  public static int migrate(Configuration host) {
    return flyway(host).migrate().migrationsExecuted;
  }

  /**
   * Validates the governance schema against its files.
   *
   * @param host the host service's Flyway configuration
   */
  public static void validate(Configuration host) {
    flyway(host).validate();
  }

  private static Flyway flyway(Configuration host) {
    return Flyway.configure()
        .configuration(host)
        .locations(LOCATION)
        .table(HISTORY_TABLE)
        .baselineVersion("0")
        .load();
  }
}
