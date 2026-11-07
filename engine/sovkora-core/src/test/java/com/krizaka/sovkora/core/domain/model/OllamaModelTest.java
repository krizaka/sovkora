package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OllamaModelTest {

  @Test
  void validConstruction() {
    var model = new OllamaModel("llama3:8b", "llama3", "sha256:abc");
    assertEquals("llama3:8b", model.name());
    assertEquals("llama3", model.model());
    assertEquals("sha256:abc", model.digest());
  }

  @Test
  void nullName_throws() {
    assertThrows(NullPointerException.class, () -> new OllamaModel(null, "model", "digest"));
  }

  @Test
  void nullModel_throws() {
    assertThrows(NullPointerException.class, () -> new OllamaModel("name", null, "digest"));
  }

  @Test
  void nullDigest_throws() {
    assertThrows(NullPointerException.class, () -> new OllamaModel("name", "model", null));
  }
}
