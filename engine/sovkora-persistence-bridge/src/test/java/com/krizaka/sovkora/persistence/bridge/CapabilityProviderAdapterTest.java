package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.CapabilityDescriptor;
import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CapabilityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityProviderAdapterTest {

  private final CapabilityManager capabilityManager = mock(CapabilityManager.class);
  private final CapabilityProviderAdapter service =
      new CapabilityProviderAdapter(capabilityManager);

  private static CapabilityDeclaration dto(String key, boolean enabled) {
    return new CapabilityDeclaration(
        key,
        "video.generate",
        "job.video.generate",
        "OUTPUT_SECOND",
        "VIDEO",
        "BATCH",
        "{}",
        "{}",
        enabled);
  }

  @Test
  void findAll_mapsDtosToDescriptors() {
    when(capabilityManager.findAll())
        .thenReturn(
            List.of(
                dto("orazaka.core.media.video", true),
                dto("orazaka.core.media.audio.analysis", false)));

    List<CapabilityDescriptor> result = service.findAll();

    assertEquals(2, result.size());
    CapabilityDescriptor first = result.getFirst();
    // A descriptor is an identity and a switch since ADR-069 §5 — the label, the icon and the
    // endpoint it used to carry were the registry's UI manifest.
    assertEquals("orazaka.core.media.video", first.featureKey());
    assertTrue(first.enabled());
    assertFalse(result.get(1).enabled());
  }

  @Test
  void findAll_empty_returnsEmptyList() {
    when(capabilityManager.findAll()).thenReturn(List.of());
    assertTrue(service.findAll().isEmpty());
  }

  @Test
  void constructor_nullManager_throws() {
    assertThrows(NullPointerException.class, () -> new CapabilityProviderAdapter(null));
  }
}
