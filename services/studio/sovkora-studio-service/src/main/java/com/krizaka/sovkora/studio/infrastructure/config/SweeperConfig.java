package com.krizaka.sovkora.studio.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the sweepers' settings and the clock they read. */
@Configuration
@EnableConfigurationProperties(DispatchStallProperties.class)
class SweeperConfig {

  /**
   * The one clock the sweepers read, so a test can hold time still.
   *
   * @return the UTC system clock
   */
  @Bean
  Clock sweeperClock() {
    return Clock.systemUTC();
  }
}
