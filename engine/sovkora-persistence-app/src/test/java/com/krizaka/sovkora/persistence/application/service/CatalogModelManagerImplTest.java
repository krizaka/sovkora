package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.AiProviderEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.AiProviderRepository;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogModelManagerImplTest {

  @Mock private CatalogModelRepository repository;
  @Mock private AiProviderRepository providerRepository;

  @InjectMocks private CatalogModelManagerImpl manager;

  @Test
  @DisplayName("getAllModels retrieves and maps all models")
  void getAllModels() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findAll()).thenReturn(List.of(entity));

    List<CatalogModelDto> result = manager.getAllModels();

    assertEquals(1, result.size());
    assertEquals("m", result.getFirst().modelName());
  }

  @Test
  @DisplayName("getModelsByCategory retrieves and maps models by category")
  void getModelsByCategory() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findByCategory("c")).thenReturn(List.of(entity));

    List<CatalogModelDto> result = manager.getModelsByCategory("c");

    assertEquals(1, result.size());
    assertEquals("c", result.getFirst().category());
  }

  @Test
  @DisplayName("getDefaultModelByCategory returns mapped default model if present")
  void getDefaultModelByCategory() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findByCategoryAndIsDefaultTrue("c")).thenReturn(Optional.of(entity));

    Optional<CatalogModelDto> result = manager.getDefaultModelByCategory("c");

    assertTrue(result.isPresent());
    assertTrue(result.get().isDefault());
  }

  @Test
  @DisplayName("saveModel saves model and toggles other defaults if necessary")
  void saveModel() {
    CatalogModelDto dto = new CatalogModelDto(1, "m", "l", "c", "{}", true, "p", 10, 24, "GPU");
    CatalogModelEntity otherEntity = new CatalogModelEntity();
    otherEntity.setId(2);
    otherEntity.setCategory("c");
    otherEntity.setIsDefault(true);

    when(repository.findByCategory("c")).thenReturn(List.of(otherEntity));
    when(repository.save(any(CatalogModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    CatalogModelDto saved = manager.saveModel(dto);

    assertNotNull(saved);
    assertTrue(saved.isDefault());
    verify(repository).save(otherEntity);
    assertFalse(otherEntity.getIsDefault());
  }

  @Test
  @DisplayName("deleteModel deletes model by ID")
  void deleteModel() {
    doNothing().when(repository).deleteById(1);
    manager.deleteModel(1);
    verify(repository).deleteById(1);
  }

  @Test
  @DisplayName("getAllProviders returns all provider names")
  void getAllProviders() {
    AiProviderEntity provider = new AiProviderEntity();
    provider.setName("p");
    provider.setBaseUrl("url");

    when(providerRepository.findAll()).thenReturn(List.of(provider));

    List<String> result = manager.getAllProviders();

    assertEquals(1, result.size());
    assertEquals("p", result.getFirst());
  }

  @Test
  @DisplayName("getProviderBaseUrl returns base URL for provider")
  void getProviderBaseUrl() {
    AiProviderEntity provider = new AiProviderEntity();
    provider.setName("p");
    provider.setBaseUrl("url");

    when(providerRepository.findByName("p")).thenReturn(Optional.of(provider));

    String url = manager.getProviderBaseUrl("p");

    assertEquals("url", url);
  }

  @Test
  @DisplayName("getProviderApiKey returns the provider's key, null for an unknown provider")
  void getProviderApiKey() {
    AiProviderEntity provider = new AiProviderEntity();
    provider.setName("do-inference");
    provider.setBaseUrl("https://inference.do-ai.run");
    provider.setApiKey("sk-test");

    when(providerRepository.findByName("do-inference")).thenReturn(Optional.of(provider));
    when(providerRepository.findByName("nope")).thenReturn(Optional.empty());

    assertEquals("sk-test", manager.getProviderApiKey("do-inference"));
    assertNull(manager.getProviderApiKey("nope"));
  }

  @Test
  @DisplayName("a model is read with where it runs, its status, typical time and price")
  void readsUserFacingFacts() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(7);
    entity.setModelName("sd-hosted");
    entity.setModelLabel("SD hosted");
    entity.setCategory("image");
    entity.setRunsOn("provider");
    entity.setStatus("available");
    entity.setTypicalSeconds(8);
    entity.setPriceCents(8);
    when(repository.findByCategory("image")).thenReturn(List.of(entity));

    CatalogModelDto dto = manager.getModelsByCategory("image").getFirst();

    assertTrue(dto.isHosted());
    assertTrue(dto.isOffered());
    assertEquals(8, dto.typicalSeconds());
    assertEquals(8, dto.priceCents());
  }

  @Test
  @DisplayName("editing through the narrow DTO keeps the stored facts")
  void editKeepsStoredFacts() {
    CatalogModelEntity stored = new CatalogModelEntity();
    stored.setId(7);
    stored.setModelName("sd-hosted");
    stored.setModelLabel("SD hosted");
    stored.setCategory("image");
    stored.setRunsOn("provider");
    stored.setTypicalSeconds(8);
    stored.setPriceCents(8);
    when(repository.findById(7)).thenReturn(Optional.of(stored));
    when(repository.save(any(CatalogModelEntity.class))).thenAnswer(call -> call.getArgument(0));

    CatalogModelDto saved =
        manager.saveModel(new CatalogModelDto(7, "sd-hosted", "SD renamed", "image", null, false));

    assertEquals("SD renamed", saved.modelLabel());
    assertTrue(saved.isHosted());
    assertEquals(8, saved.typicalSeconds());
    assertEquals(8, saved.priceCents());
  }
}
