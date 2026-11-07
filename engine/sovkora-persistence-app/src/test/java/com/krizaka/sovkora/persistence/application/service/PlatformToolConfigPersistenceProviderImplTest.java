package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.PlatformToolConfigDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformToolConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformToolConfigRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformToolConfigPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private PlatformToolConfigRepository repository;

  @InjectMocks private PlatformToolConfigPersistenceProviderImpl provider;

  @Test
  @DisplayName("findByToolId retrieves tool configuration by tool ID")
  void findByToolId() {
    PlatformToolConfigEntity entity = new PlatformToolConfigEntity();
    entity.setId(1);
    entity.setToolId("tool-1");
    entity.setCacheEnabled(true);
    entity.setCacheTtlSeconds(600);

    when(repository.findByToolId("tool-1")).thenReturn(Optional.of(entity));

    Optional<PlatformToolConfigDto> result = provider.findByToolId("tool-1");

    assertTrue(result.isPresent());
    assertEquals("tool-1", result.get().toolId());
  }

  @Test
  @DisplayName("save persists tool configuration details")
  void save() {
    Instant now = Instant.now(FIXED_CLOCK);
    PlatformToolConfigDto dto =
        new PlatformToolConfigDto(1, "tool-1", true, 600, false, "FIXED", "table", now);
    PlatformToolConfigEntity entity = PlatformToolConfigPersistenceProviderImpl.toEntity(dto);

    when(repository.save(any(PlatformToolConfigEntity.class))).thenReturn(entity);

    PlatformToolConfigDto saved = provider.save(dto);

    assertNotNull(saved);
    assertEquals("tool-1", saved.toolId());
  }
}
