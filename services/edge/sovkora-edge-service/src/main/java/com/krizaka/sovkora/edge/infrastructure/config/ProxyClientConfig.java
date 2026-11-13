package com.krizaka.sovkora.edge.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP client of the streaming proxy: {@link RestClient} over the Spring-managed JDK
 * {@link HttpClient} factory (AGENTS.md §4 — RestClient only, no hand-rolled HTTP client).
 *
 * <p>Both timeouts come from {@code sovkora.edge.backend.timeouts} (krizaka-internal#2132): the
 * connect, and the wait for the response headers. No read timeout on the body on purpose: SSE
 * streams (chat tokens, job progress) stay open for the whole interaction; a parked virtual thread
 * per open stream is the §4 concurrency model. A backend that refuses or does not answer in time is
 * a coded 503 at the proxy.
 */
@Configuration
class ProxyClientConfig {

  @Bean
  RestClient proxyRestClient(EdgeBackendProperties backend) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(backend.timeouts().connect()).build();
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            new ResponseTimeoutHttpClient(httpClient, backend.timeouts().response()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  /** The edge's clock: when a backend's circuit opened, and so its Retry-After. */
  @Bean
  Clock edgeClock() {
    return Clock.systemUTC();
  }
}
