package com.krizaka.sovkora.core.application.service;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.CatalogModelInfo;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultCatalogModelServiceTest {

  @Mock private CatalogModelManager catalogModelManager;

  private DefaultCatalogModelService service;

  @BeforeEach
  void setUp() {
    service = new DefaultCatalogModelService(catalogModelManager);
  }

  @Test
  void constructorShouldThrowOnNullManager() {
    assertThatThrownBy(() -> new DefaultCatalogModelService(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("CatalogModelManager cannot be null");
  }

  @Test
  void shouldGetAllModels() {
    CatalogModelDto dto =
        new CatalogModelDto(1, MODEL_M1, LABEL_L1, CATEGORY, OPTS, true, PROVIDER_OLLAMA);
    when(catalogModelManager.getAllModels()).thenReturn(List.of(dto));

    List<CatalogModelInfo> result = service.getAllModels();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().modelName()).isEqualTo(MODEL_M1);
    verify(catalogModelManager).getAllModels();
  }

  @Test
  void shouldGetModelsByCategory() {
    CatalogModelDto dto =
        new CatalogModelDto(1, MODEL_M1, LABEL_L1, CATEGORY, OPTS, true, PROVIDER_OLLAMA);
    when(catalogModelManager.getModelsByCategory(CATEGORY)).thenReturn(List.of(dto));

    List<CatalogModelInfo> result = service.getModelsByCategory(CATEGORY);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().category()).isEqualTo(CATEGORY);
    verify(catalogModelManager).getModelsByCategory(CATEGORY);
  }

  @Test
  void shouldGetDefaultModelByCategory() {
    CatalogModelDto dto =
        new CatalogModelDto(1, MODEL_M1, LABEL_L1, CATEGORY, OPTS, true, PROVIDER_OLLAMA);
    when(catalogModelManager.getDefaultModelByCategory(CATEGORY)).thenReturn(Optional.of(dto));

    Optional<CatalogModelInfo> result = service.getDefaultModelByCategory(CATEGORY);

    assertThat(result).isPresent();
    assertThat(result.get().isDefault()).isTrue();
    verify(catalogModelManager).getDefaultModelByCategory(CATEGORY);
  }

  @Test
  void shouldSaveModel() {
    CatalogModelInfo info =
        new CatalogModelInfo(1, MODEL_M1, LABEL_L1, CATEGORY, OPTS, true, PROVIDER_OLLAMA);
    CatalogModelDto dto =
        new CatalogModelDto(1, MODEL_M1, LABEL_L1, CATEGORY, OPTS, true, PROVIDER_OLLAMA);
    when(catalogModelManager.saveModel(dto)).thenReturn(dto);

    CatalogModelInfo result = service.saveModel(info);

    assertThat(result.modelName()).isEqualTo(MODEL_M1);
    verify(catalogModelManager).saveModel(dto);
  }

  @Test
  void shouldDeleteModel() {
    service.deleteModel(1);
    verify(catalogModelManager).deleteModel(1);
  }
}
