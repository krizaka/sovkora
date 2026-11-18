package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer.Groups;

/**
 * Configures the {@value #GROUP} HTTP service group once: the read timeout and the worker's service
 * token ({@code AI_READER_SERVICE_TOKEN}), so no declared method can forget it. The base URL is
 * Spring Boot's group property {@code spring.http.serviceclient.reader.base-url} ({@code
 * AI_READER_URL}).
 */
final class ReaderHttpGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

  /** The group the reader exchange is imported into. */
  static final String GROUP = "reader";

  private final String serviceToken;
  private final Duration readTimeout;

  ReaderHttpGroupConfigurer(String serviceToken, Duration readTimeout) {
    this.serviceToken = serviceToken;
    this.readTimeout = readTimeout;
  }

  @Override
  public void configureGroups(Groups<RestClient.Builder> groups) {
    groups.filterByName(GROUP).forEachClient((group, builder) -> configure(builder));
  }

  void configure(RestClient.Builder builder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(2));
    requestFactory.setReadTimeout(readTimeout);
    builder.requestFactory(requestFactory);
    if (!serviceToken.isBlank()) {
      builder.requestInitializer(request -> request.getHeaders().setBearerAuth(serviceToken));
    }
  }
}
