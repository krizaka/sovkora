package com.krizaka.sovkora.adapter.springai.routing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DynamicModelRouterTest {

  @Test
  void missingProviderEndpointAllowsDeclaredStepFallback() {
    var resolver = mock(ModelEndpointResolver.class);
    var proxy = mock(UniversalProxyChatProvider.class);
    when(resolver.resolve("primary")).thenReturn(Optional.empty());

    assertThrows(
        StepModelUnavailableException.class,
        () -> new DynamicModelRouter(resolver, proxy).chatModel("primary"));
    verifyNoInteractions(proxy);
  }
}
