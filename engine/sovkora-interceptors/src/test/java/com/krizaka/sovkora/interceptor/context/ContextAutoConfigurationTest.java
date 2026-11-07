package com.krizaka.sovkora.interceptor.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.application.pipeline.PipelineRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextAutoConfigurationTest {

  @Test
  @DisplayName(
      "ContextAutoConfiguration registers UserContextResolver, UserContextInterceptor, and SystemContextInjector")
  void registersBeans() {
    ContextAutoConfiguration config = new ContextAutoConfiguration();
    PipelineRegistry pipelineRegistry = mock(PipelineRegistry.class);

    UserContextResolver resolver = config.userContextResolver();
    UserContextInterceptor interceptor = config.userContextInterceptor(pipelineRegistry);
    SystemContextInjector injector = config.systemContextInjector();

    assertThat(resolver).isNotNull();
    assertThat(interceptor).isNotNull();
    assertThat(injector).isNotNull();
  }
}
