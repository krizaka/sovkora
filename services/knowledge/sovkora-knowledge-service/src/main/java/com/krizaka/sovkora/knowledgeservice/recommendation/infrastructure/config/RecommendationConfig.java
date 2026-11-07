package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** The recommender's beans: the clock, the embedding client, the retention schedule. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RecommendationProperties.class)
class RecommendationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RestClient recommendationEmbeddingRestClient(RecommendationProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.embeddingsTimeout());
    factory.setReadTimeout(properties.embeddingsTimeout());
    return RestClient.builder()
        .baseUrl(properties.embeddingsBaseUrl().toString())
        .requestFactory(factory)
        .build();
  }
}
