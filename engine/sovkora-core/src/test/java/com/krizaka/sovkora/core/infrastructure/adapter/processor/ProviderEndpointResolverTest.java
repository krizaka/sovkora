package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProviderEndpointResolver}. */
class ProviderEndpointResolverTest {

  @Test
  void localAiBaseUrl_catalogHasUrl_returnsIt() {
    CatalogModelManager manager = mock(CatalogModelManager.class);
    when(manager.getProviderBaseUrl("localai")).thenReturn("http://my-localai:9090");
    assertEquals("http://my-localai:9090", new ProviderEndpointResolver(manager).localAiBaseUrl());
  }

  @Test
  void localAiBaseUrl_catalogReturnsNull_fallsBack() {
    CatalogModelManager manager = mock(CatalogModelManager.class);
    when(manager.getProviderBaseUrl("localai")).thenReturn(null);
    assertTrue(new ProviderEndpointResolver(manager).localAiBaseUrl().contains("8085"));
  }

  @Test
  void localAiBaseUrl_catalogReturnsBlank_fallsBack() {
    CatalogModelManager manager = mock(CatalogModelManager.class);
    when(manager.getProviderBaseUrl("localai")).thenReturn("  ");
    assertTrue(new ProviderEndpointResolver(manager).localAiBaseUrl().contains("8085"));
  }

  @Test
  void localAiBaseUrl_nullCatalog_fallsBack() {
    assertTrue(new ProviderEndpointResolver(null).localAiBaseUrl().contains("8085"));
  }
}
