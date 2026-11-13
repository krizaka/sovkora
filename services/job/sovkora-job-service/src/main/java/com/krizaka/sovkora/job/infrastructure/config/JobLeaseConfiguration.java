package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.sovkora.job.domain.model.JobLeasePolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The one clock the job lease and its sweeper read, so a test can move it. */
@Configuration
class JobLeaseConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  JobLeasePolicy jobLeasePolicy(JobLeaseProperties properties) {
    return properties.toPolicy();
  }
}
