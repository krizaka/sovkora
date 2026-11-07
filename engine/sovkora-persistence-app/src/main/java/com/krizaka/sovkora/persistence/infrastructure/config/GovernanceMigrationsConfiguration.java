package com.krizaka.sovkora.persistence.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link GovernanceMigrations} into a host service that has Flyway and opts in with {@code
 * orazaka.governance.migrations.enabled=true} (krizaka-internal#2255). No {@link Flyway} bean is
 * defined here: that would switch the host's own Flyway auto-configuration off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Flyway.class, FlywayMigrationStrategy.class})
@ConditionalOnProperty(name = GovernanceMigrations.ENABLED_PROPERTY, havingValue = "true")
class GovernanceMigrationsConfiguration {

  @Bean
  FlywayMigrationStrategy governanceMigrations() {
    return new GovernanceMigrations();
  }
}
