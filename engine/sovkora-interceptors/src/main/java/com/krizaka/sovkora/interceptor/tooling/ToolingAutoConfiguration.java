package com.krizaka.sovkora.interceptor.tooling;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolOptions;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the tooling interceptor module.
 *
 * <p>Conditionally registers the {@link ToolInterceptor} bean only when a {@link ToolRegistry} is
 * present in the application context, with the {@link ToolOptions} port that offers its tools to a
 * provider.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class ToolingAutoConfiguration {

  @Bean
  @ConditionalOnBean({ToolRegistry.class, ToolOptions.class})
  ToolInterceptor toolInterceptor(ToolRegistry toolRegistry, ToolOptions toolOptions) {
    return new ToolInterceptor(toolRegistry, toolOptions);
  }
}
