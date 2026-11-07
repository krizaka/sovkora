package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.core.infrastructure.support.SecurityContextUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class DefaultMcpOrchestratorTest {

  @Mock private PlatformMcpServerProvider platformMcpServerProvider;

  @Mock private UserMcpServerProvider userMcpServerProvider;

  @Mock private RestClient.Builder restClientBuilder;

  private DefaultMcpOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new DefaultMcpOrchestrator(
            platformMcpServerProvider, userMcpServerProvider, restClientBuilder);
  }

  @Test
  void resolveExternalContext_noEndpoints_returnsEmpty() {
    when(platformMcpServerProvider.getActivePlatformMcpServers()).thenReturn(List.of());

    String result = orchestrator.resolveExternalContext();

    assertEquals("", result);
  }

  @Test
  void resolveExternalTools_returnsEmptyList() {
    List<Object> result = orchestrator.resolveExternalTools();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void resolveExternalContext_nullPlatformProvider_handlesGracefully() {
    var orch = new DefaultMcpOrchestrator(null, userMcpServerProvider, restClientBuilder);
    String result = orch.resolveExternalContext();
    assertEquals("", result);
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolveExternalContext_withPlatformAndUserServers_success() {
    var platformServer =
        new PlatformMcpServerProvider.PlatformMcpServer(
            1, "Platform", "REMOTE", "http://platform-mcp", null, null, null, true);
    when(platformMcpServerProvider.getActivePlatformMcpServers())
        .thenReturn(List.of(platformServer));

    var userServer =
        new UserMcpServerProvider.UserMcpServer(1, "user-1", "User", "http://user-mcp", null, true);
    when(userMcpServerProvider.getActiveUserMcpServers("user-1")).thenReturn(List.of(userServer));

    var restClient = mock(RestClient.class);
    var uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClientBuilder.clone()).thenReturn(restClientBuilder);
    when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
    when(restClientBuilder.build()).thenReturn(restClient);
    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn("context-data");

    try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
      mockedSecurity
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", "user-1"));

      String result = orchestrator.resolveExternalContext();

      assertEquals("context-data\ncontext-data", result);
    }
  }

  @Test
  void resolveExternalContext_fetchThrowsException_returnsEmptyString() {
    var platformServer =
        new PlatformMcpServerProvider.PlatformMcpServer(
            1, "Platform", "REMOTE", "http://platform-mcp", null, null, null, true);
    when(platformMcpServerProvider.getActivePlatformMcpServers())
        .thenReturn(List.of(platformServer));

    when(restClientBuilder.clone()).thenThrow(new RuntimeException("clone failed"));

    String result = orchestrator.resolveExternalContext();

    assertEquals("", result);
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolveExternalContext_fetchReturnsNullBody_handledGracefully() {
    var platformServer =
        new PlatformMcpServerProvider.PlatformMcpServer(
            1, "Platform", "REMOTE", "http://platform-mcp", null, null, null, true);
    when(platformMcpServerProvider.getActivePlatformMcpServers())
        .thenReturn(List.of(platformServer));

    var restClient = mock(RestClient.class);
    var uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClientBuilder.clone()).thenReturn(restClientBuilder);
    when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
    when(restClientBuilder.build()).thenReturn(restClient);
    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(null);

    String result = orchestrator.resolveExternalContext();

    assertEquals("", result);
  }
}
