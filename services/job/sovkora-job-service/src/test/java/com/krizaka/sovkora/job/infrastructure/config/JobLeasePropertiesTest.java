package com.krizaka.sovkora.job.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** What the {@code sovkora.jobs.lease} keys read when unset and when written. */
class JobLeasePropertiesTest {

  @Test
  @DisplayName("unset, the lease is 120s, renewed every 30s, retried twice")
  void defaults() {
    JobLeaseProperties properties =
        new Binder(new MapConfigurationPropertySource())
            .bindOrCreate("sovkora.jobs.lease", Bindable.of(JobLeaseProperties.class));

    assertThat(properties.toPolicy().lease()).isEqualTo(Duration.ofSeconds(120));
    assertThat(properties.toPolicy().renewInterval()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.toPolicy().maxRetries()).isEqualTo(2);
  }

  @Test
  @DisplayName("written, the keys are seconds and a retry count")
  void written() {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            java.util.Map.of(
                "sovkora.jobs.lease.lease-seconds", "300",
                "sovkora.jobs.lease.renew-interval-seconds", "60",
                "sovkora.jobs.lease.max-retries", "4"));

    JobLeaseProperties properties =
        new Binder(source)
            .bindOrCreate("sovkora.jobs.lease", Bindable.of(JobLeaseProperties.class));

    assertThat(properties.toPolicy().lease()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.toPolicy().maxRetries()).isEqualTo(4);
  }
}
