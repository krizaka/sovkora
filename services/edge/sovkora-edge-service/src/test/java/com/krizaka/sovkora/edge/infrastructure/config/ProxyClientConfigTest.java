package com.krizaka.sovkora.edge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProxyClientConfigTest {

  @Test
  @DisplayName("Builds the streaming proxy client from the backend timeouts (JDK factory)")
  void buildsProxyClient() {
    var backend =
        new EdgeBackendProperties(
            new EdgeBackendProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(60)),
            new EdgeBackendProperties.Breaker(
                50, Duration.ofSeconds(30), 80, 20, 10, Duration.ofSeconds(30), 3),
            Duration.ofSeconds(5));

    assertThat(new ProxyClientConfig().proxyRestClient(backend)).isNotNull();
    assertThat(new ProxyClientConfig().edgeClock()).isNotNull();
  }
}
