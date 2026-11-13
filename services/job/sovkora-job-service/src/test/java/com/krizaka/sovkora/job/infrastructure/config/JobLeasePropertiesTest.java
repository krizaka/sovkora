package com.krizaka.sovkora.job.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** What the {@code orazaka.jobs.lease} keys read when unset and when written. */
class JobLeasePropertiesTest {

  @Test
  @DisplayName("unset, the lease is 120s, renewed every 30s, retried twice")
  void defaults() {
    JobLeaseProperties properties =
        new Binder(new MapConfigurationPropertySource())
            .bindOrCreate("orazaka.jobs.lease", Bindable.of(JobLeaseProperties.class));

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
                "orazaka.jobs.lease.lease-seconds", "300",
                "orazaka.jobs.lease.renew-interval-seconds", "60",
                "orazaka.jobs.lease.max-retries", "4"));

    JobLeaseProperties properties =
        new Binder(source)
            .bindOrCreate("orazaka.jobs.lease", Bindable.of(JobLeaseProperties.class));

    assertThat(properties.toPolicy().lease()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.toPolicy().maxRetries()).isEqualTo(4);
  }
}
