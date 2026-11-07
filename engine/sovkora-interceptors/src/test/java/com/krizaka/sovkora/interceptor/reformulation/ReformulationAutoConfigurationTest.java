package com.krizaka.sovkora.interceptor.reformulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class ReformulationAutoConfigurationTest {

  @Test
  @DisplayName(
      "ReformulationAutoConfiguration registers refiner, router and Semantic router interceptor beans")
  void registersBeans() {
    ReformulationAutoConfiguration config = new ReformulationAutoConfiguration();
    CoreProperties props = mock(CoreProperties.class);
    ProviderChatClient chatClient = mock(ProviderChatClient.class);
    Resource systemRefinement = mock(Resource.class);
    Resource contextEnvelope = mock(Resource.class);
    Resource routerSystem = mock(Resource.class);

    RefinerInterceptor refiner =
        config.refinerInterceptor(chatClient, props, systemRefinement, contextEnvelope);
    assertThat(refiner).isNotNull();

    RouterInterceptor router = config.routerInterceptor(chatClient, props, routerSystem);
    assertThat(router).isNotNull();

    SemanticRouterInterceptor semanticRouter = config.semanticRouterInterceptor();
    assertThat(semanticRouter).isNotNull();
  }
}
