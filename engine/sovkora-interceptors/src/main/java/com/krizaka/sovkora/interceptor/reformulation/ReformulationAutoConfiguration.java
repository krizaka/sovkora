package com.krizaka.sovkora.interceptor.reformulation;

import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

/**
 * Spring Boot auto-configuration for the reformulation interceptor module.
 *
 * <p>Conditionally registers {@link RefinerInterceptor} and {@link RouterInterceptor} beans only
 * when their dependencies ({@link ProviderChatClient}, {@link CoreProperties}) are present in the
 * application context.
 *
 * @since 1.0.0
 */
@AutoConfiguration(
    afterName = "com.krizaka.sovkora.adapter.springai.SpringAiAdapterAutoConfiguration")
public class ReformulationAutoConfiguration {

  /**
   * Registers the {@link RefinerInterceptor} bean.
   *
   * @param chatClient The port that asks a provider's model.
   * @param properties Core configuration properties.
   * @param systemRefinementResource Refinement system prompt template.
   * @param contextEnvelopeResource Context envelope user prompt template.
   * @return The refiner interceptor.
   */
  @Bean
  @ConditionalOnBean(ProviderChatClient.class)
  public RefinerInterceptor refinerInterceptor(
      ProviderChatClient chatClient,
      CoreProperties properties,
      @Value("classpath:/prompts/system-refinement.st") Resource systemRefinementResource,
      @Value("classpath:/prompts/context-envelope.st") Resource contextEnvelopeResource) {
    return new RefinerInterceptor(
        chatClient, properties, systemRefinementResource, contextEnvelopeResource);
  }

  /**
   * Registers the {@link RouterInterceptor} bean.
   *
   * @param chatClient The port that asks a provider's model.
   * @param properties Core configuration properties.
   * @param routerSystemResource Router system prompt template.
   * @return The router interceptor.
   */
  @Bean
  @ConditionalOnBean(ProviderChatClient.class)
  public RouterInterceptor routerInterceptor(
      ProviderChatClient chatClient,
      CoreProperties properties,
      @Value("classpath:/prompts/system-router.st") Resource routerSystemResource) {
    return new RouterInterceptor(chatClient, properties, routerSystemResource);
  }

  /**
   * Registers the {@link SemanticRouterInterceptor} bean.
   *
   * <p>Evaluates multi-intent requests and decomposes them into SIM-DAG dependency graphs for
   * sequential or parallel multimedia task orchestration.
   *
   * @return The SIM-DAG router interceptor.
   */
  @Bean
  SemanticRouterInterceptor semanticRouterInterceptor() {
    return new SemanticRouterInterceptor();
  }
}
