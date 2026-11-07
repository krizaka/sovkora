package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CatalogModelInfoTest {

  @Test
  void fullConstruction() {
    var info =
        new CatalogModelInfo(1, "llama3", "Llama 3", "chat", "{}", true, "openai", 10, 24, "CUDA");
    assertEquals(1, info.id());
    assertEquals("llama3", info.modelName());
    assertEquals("Llama 3", info.modelLabel());
    assertEquals("chat", info.category());
    assertTrue(info.isDefault());
    assertEquals("openai", info.providerName());
    assertEquals(10, info.maxSteps());
    assertEquals(24, info.recommendedFps());
    assertEquals("CUDA", info.supportedHardware());
  }

  @Test
  void shortConstructor_6args() {
    var info = new CatalogModelInfo(1, "model", "Model", "chat", "{}", false);
    assertEquals("ollama", info.providerName());
    assertFalse(info.isDefault());
  }

  @Test
  void shortConstructor_7args() {
    var info = new CatalogModelInfo(1, "model", "Model", "chat", "{}", true, "openai");
    assertEquals("openai", info.providerName());
  }

  @Test
  void nullIsDefault_defaultsToFalse() {
    var info = new CatalogModelInfo(1, "model", "Model", "chat", "{}", null);
    assertFalse(info.isDefault());
  }

  @Test
  void nullProviderName_defaultsToOllama() {
    var info = new CatalogModelInfo(1, "model", "Model", "chat", "{}", false, null);
    assertEquals("ollama", info.providerName());
  }

  @Test
  void blankProviderName_defaultsToOllama() {
    var info = new CatalogModelInfo(1, "model", "Model", "chat", "{}", false, "  ");
    assertEquals("ollama", info.providerName());
  }

  @Test
  void nullModelName_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new CatalogModelInfo(1, null, "label", "chat", "{}", false));
  }

  @Test
  void blankModelName_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CatalogModelInfo(1, "  ", "label", "chat", "{}", false));
  }

  @Test
  void blankCategory_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CatalogModelInfo(1, "model", "label", "  ", "{}", false));
  }
}
