package com.krizaka.sovkora.interceptor.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolOptions;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolingAutoConfigurationTest {

  @Test
  @DisplayName("ToolingAutoConfiguration registers the ToolInterceptor bean")
  void registersBeans() {
    ToolingAutoConfiguration config = new ToolingAutoConfiguration();
    ToolRegistry toolRegistry = mock(ToolRegistry.class);

    ToolInterceptor interceptor = config.toolInterceptor(toolRegistry, mock(ToolOptions.class));

    assertThat(interceptor).isNotNull();
  }
}
