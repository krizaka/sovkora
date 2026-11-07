package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CapabilityEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CapabilityRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CapabilityManagerImplTest {

  private final CapabilityRepository repository = mock(CapabilityRepository.class);
  private final CapabilityManagerImpl manager = new CapabilityManagerImpl(repository);

  private static CapabilityEntity entity(String key, Boolean enabled) {
    var e = new CapabilityEntity();
    e.setFeatureKey(key);
    e.setHandlerKey("video.generate");
    e.setRoutingKey("job.video.generate");
    e.setBillableUnit("OUTPUT_SECOND");
    e.setBillableCapability("VIDEO");
    e.setInputSchema("{}");
    e.setOutputSchema("{}");
    e.setIsEnabled(enabled);
    return e;
  }

  @Test
  void findAll_mapsEntitiesToDtos() {
    when(repository.findAll()).thenReturn(List.of(entity("orazaka.core.media.video", true)));
    List<CapabilityDeclaration> result = manager.findAll();
    assertEquals(1, result.size());
    assertEquals("video.generate", result.getFirst().handlerKey());
    assertEquals("job.video.generate", result.getFirst().routingKey());
    assertTrue(result.getFirst().enabled());
  }

  @Test
  void findAll_nullIsEnabled_mapsToFalse() {
    when(repository.findAll()).thenReturn(List.of(entity("orazaka.core.chat.completion", null)));
    assertFalse(manager.findAll().getFirst().enabled());
  }

  @Test
  void findByFeatureKey_found_returnsDto() {
    when(repository.findById("orazaka.core.chat.completion"))
        .thenReturn(Optional.of(entity("orazaka.core.chat.completion", true)));
    assertEquals(
        "video.generate",
        manager.findByFeatureKey("orazaka.core.chat.completion").orElseThrow().handlerKey());
  }

  @Test
  void findByFeatureKey_absent_returnsEmpty() {
    when(repository.findById("nope")).thenReturn(Optional.empty());
    assertTrue(manager.findByFeatureKey("nope").isEmpty());
  }

  @Test
  void save_roundTripsThroughRepository() {
    var dto =
        new CapabilityDeclaration(
            "orazaka.core.chat.completion",
            "h",
            "job.text.process",
            null,
            "CHAT",
            "BATCH",
            "{}",
            "{}",
            true);
    when(repository.save(any(CapabilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    CapabilityDeclaration saved = manager.save(dto);
    assertEquals("orazaka.core.chat.completion", saved.featureKey());
    assertEquals("h", saved.handlerKey());
    assertEquals("job.text.process", saved.routingKey());
    verify(repository).save(any(CapabilityEntity.class));
  }
}
