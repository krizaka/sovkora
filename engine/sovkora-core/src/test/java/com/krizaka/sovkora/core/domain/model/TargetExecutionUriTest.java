package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TargetExecutionUriTest {

  @Test
  void validConstruction() {
    var uri = new TargetExecutionUri("/api/v1/generate", "POST", "{\"prompt\":\"$PROMPT\"}");
    assertEquals("/api/v1/generate", uri.uriPath());
    assertEquals("POST", uri.httpMethod());
    assertEquals("{\"prompt\":\"$PROMPT\"}", uri.payloadTemplate());
  }

  @Test
  void nullUriPath_throws() {
    assertThrows(NullPointerException.class, () -> new TargetExecutionUri(null, "GET", null));
  }

  @Test
  void blankUriPath_throws() {
    assertThrows(IllegalArgumentException.class, () -> new TargetExecutionUri("  ", "GET", null));
  }

  @Test
  void nullHttpMethod_throws() {
    assertThrows(NullPointerException.class, () -> new TargetExecutionUri("/api", null, null));
  }

  @Test
  void blankHttpMethod_throws() {
    assertThrows(IllegalArgumentException.class, () -> new TargetExecutionUri("/api", "  ", null));
  }

  @Test
  void nullPayloadTemplate_allowed() {
    var uri = new TargetExecutionUri("/api", "GET", null);
    assertNull(uri.payloadTemplate());
  }
}
