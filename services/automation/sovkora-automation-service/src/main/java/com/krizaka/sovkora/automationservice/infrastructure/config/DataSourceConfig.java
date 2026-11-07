package com.krizaka.sovkora.automationservice.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the service's {@link DataSource} explicitly from {@link AutomationDataSourceProperties} so
 * the connection can only come from {@code AUTOMATION_DB_*} wiring — defining the bean makes Boot's
 * {@code spring.datasource} autoconfiguration (and any {@code SPRING_DATASOURCE_*} environment
 * pollution) back off entirely.
 */
@Configuration
@EnableConfigurationProperties(AutomationDataSourceProperties.class)
class DataSourceConfig {

  @Bean
  DataSource automationDataSource(AutomationDataSourceProperties properties) {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(properties.url());
    dataSource.setUsername(properties.username());
    dataSource.setPassword(properties.password());
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setMaximumPoolSize(properties.maxPoolSize());
    dataSource.setMinimumIdle(Math.min(1, properties.maxPoolSize()));
    return dataSource;
  }
}
