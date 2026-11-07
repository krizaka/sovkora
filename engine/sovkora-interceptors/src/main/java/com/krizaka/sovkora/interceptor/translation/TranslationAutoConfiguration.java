package com.krizaka.sovkora.interceptor.translation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the translation interceptor module.
 *
 * <p>Registered via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class TranslationAutoConfiguration {

  /**
   * Registers the {@link LanguageAlignmentInterceptor} as a Spring bean.
   *
   * @return The language alignment interceptor instance.
   */
  @Bean
  public LanguageAlignmentInterceptor languageAlignmentInterceptor() {
    return new LanguageAlignmentInterceptor();
  }
}
