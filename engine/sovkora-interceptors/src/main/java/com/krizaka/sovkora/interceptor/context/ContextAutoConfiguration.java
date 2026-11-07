package com.krizaka.sovkora.interceptor.context;

import com.krizaka.sovkora.core.application.pipeline.PipelineRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the context interceptor module.
 *
 * <p>Registers {@link UserContextResolver}, {@link UserContextInterceptor}, and {@link
 * SystemContextInjector} beans into the application context for dynamic pipeline discovery.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class ContextAutoConfiguration {

  @Bean
  UserContextResolver userContextResolver() {
    return new UserContextResolver();
  }

  @Bean
  UserContextInterceptor userContextInterceptor(PipelineRegistry pipelineRegistry) {
    return new UserContextInterceptor(pipelineRegistry);
  }

  @Bean
  SystemContextInjector systemContextInjector() {
    return new SystemContextInjector();
  }
}
