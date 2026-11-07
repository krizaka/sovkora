package com.krizaka.sovkora.automationservice.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The clock every time-dependent decision reads (injected, so tests can fix it), and the wiring.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
  StudioClientProperties.class,
  SchedulerProperties.class,
  PublicUrlProperties.class
})
class RuntimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
