package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificationResponseTest {

  @Test
  void validConstruction() {
    var intents =
        List.of(
            new ClassificationResponse.ClassifiedIntent("video_generation", 0.92),
            new ClassificationResponse.ClassifiedIntent("translation", 0.45));
    var response = new ClassificationResponse(intents);
    assertEquals(2, response.intents().size());
    assertEquals("video_generation", response.intents().getFirst().label());
    assertEquals(0.92, response.intents().getFirst().confidence());
  }

  @Test
  void nullIntents_defaultsToEmptyList() {
    var response = new ClassificationResponse(null);
    assertTrue(response.intents().isEmpty());
  }

  @Test
  void intents_isImmutable() {
    var response =
        new ClassificationResponse(
            List.of(new ClassificationResponse.ClassifiedIntent("chat", 0.9)));
    var intents = response.intents();
    var newIntent = new ClassificationResponse.ClassifiedIntent("new", 0.5);
    assertThrows(UnsupportedOperationException.class, () -> intents.add(newIntent));
  }
}
