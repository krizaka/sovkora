package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaCatalogTest {

  @Test
  void validConstruction() {
    var models = List.of(new OllamaModel("llama3", "llama3", "abc"));
    var catalog = new OllamaCatalog(models);
    assertEquals(1, catalog.models().size());
  }

  @Test
  void nullModels_throws() {
    assertThrows(NullPointerException.class, () -> new OllamaCatalog(null));
  }

  @Test
  void models_isImmutable() {
    var catalog = new OllamaCatalog(List.of(new OllamaModel("a", "a", "a")));
    var models = catalog.models();
    var newModel = new OllamaModel("b", "b", "b");
    assertThrows(UnsupportedOperationException.class, () -> models.add(newModel));
  }
}
