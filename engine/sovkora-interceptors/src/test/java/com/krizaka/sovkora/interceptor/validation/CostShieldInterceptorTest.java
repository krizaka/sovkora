package com.krizaka.sovkora.interceptor.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostShieldInterceptorTest {

  private final CostShieldInterceptor interceptor = new CostShieldInterceptor();

  @Test
  void intercept_enrichesSystemMetadataWithMemoryUsage() {
    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertNotNull(result.systemMetadata().get("memoryUsagePercent"));
    assertNotNull(result.systemMetadata().get("costShieldActive"));
    assertInstanceOf(Double.class, result.systemMetadata().get("memoryUsagePercent"));
    assertInstanceOf(Boolean.class, result.systemMetadata().get("costShieldActive"));
  }

  @Test
  void intercept_preservesExistingSystemMetadata() {
    var context =
        new PromptContext(
            "test",
            Map.of(),
            Map.of("existingKey", "existingValue"),
            "test",
            null,
            com.krizaka.sovkora.core.domain.model.RoutingMode.DETERMINISTIC);
    PromptContext result = interceptor.intercept(context);

    assertEquals("existingValue", result.systemMetadata().get("existingKey"));
    assertTrue(result.systemMetadata().containsKey("memoryUsagePercent"));
  }

  @Test
  void intercept_preservesRawUserQuery() {
    var context = new PromptContext("original query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertEquals("original query", result.rawUserQuery());
  }

  @Test
  void intercept_memoryUsageIsNonNegative() {
    var context = new PromptContext("test", Map.of());
    PromptContext result = interceptor.intercept(context);

    double usage = (double) result.systemMetadata().get("memoryUsagePercent");
    assertTrue(usage >= 0.0);
  }

  @Test
  void intercept_whenMemoryUsageExceedsThreshold_setsCostShieldActiveAndRecommendsProvider() {
    try (org.mockito.MockedConstruction<ProcessBuilder> mocked =
        org.mockito.Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = org.mockito.Mockito.mock(Process.class);
              when(mockProcess.waitFor()).thenReturn(0);

              // Total pages = 100, Free pages = 5 (5%). Used pages = 60+30+0 = 90 (90%) > 85%
              // threshold
              String output =
                  """
          Mach Virtual Memory Statistics: (page size of 4096 bytes)
          Pages free:                  5.
          Pages active:                60.
          Pages inactive:              5.
          Pages wired:                 30.
          Pages occupied by compressor: 0.
          """;

              java.io.ByteArrayInputStream inputStream =
                  new java.io.ByteArrayInputStream(output.getBytes());
              when(mockProcess.getInputStream()).thenReturn(inputStream);
              when(mock.start()).thenReturn(mockProcess);
            })) {
      var context = new PromptContext("test query", Map.of());
      PromptContext result = interceptor.intercept(context);

      assertEquals(90.0, result.systemMetadata().get("memoryUsagePercent"));
      assertEquals(true, result.systemMetadata().get("costShieldActive"));
      assertEquals("openai", result.systemMetadata().get("recommendedProvider"));
    }
  }

  @Test
  void intercept_whenMemoryUsageBelowThreshold_costShieldIsPassive() {
    try (org.mockito.MockedConstruction<ProcessBuilder> mocked =
        org.mockito.Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = org.mockito.Mockito.mock(Process.class);
              when(mockProcess.waitFor()).thenReturn(0);

              // Total pages = 100, Free pages = 50 (50%). Used pages = 30+10+10 = 50 (50%) < 85%
              // threshold
              String output =
                  """
          Mach Virtual Memory Statistics: (page size of 4096 bytes)
          Pages free:                  50.
          Pages active:                30.
          Pages inactive:              0.
          Pages wired:                 10.
          Pages occupied by compressor: 10.
          """;

              java.io.ByteArrayInputStream inputStream =
                  new java.io.ByteArrayInputStream(output.getBytes());
              when(mockProcess.getInputStream()).thenReturn(inputStream);
              when(mock.start()).thenReturn(mockProcess);
            })) {
      var context = new PromptContext("test query", Map.of());
      PromptContext result = interceptor.intercept(context);

      assertEquals(50.0, result.systemMetadata().get("memoryUsagePercent"));
      assertEquals(false, result.systemMetadata().get("costShieldActive"));
      assertNull(result.systemMetadata().get("recommendedProvider"));
    }
  }

  @Test
  void intercept_whenProcessStartThrowsException_fallsBackToZeroUsage() {
    try (org.mockito.MockedConstruction<ProcessBuilder> mocked =
        org.mockito.Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              when(mock.start()).thenThrow(new java.io.IOException("failed to run command"));
            })) {
      var context = new PromptContext("test query", Map.of());
      PromptContext result = interceptor.intercept(context);

      assertEquals(0.0, result.systemMetadata().get("memoryUsagePercent"));
      assertEquals(false, result.systemMetadata().get("costShieldActive"));
    }
  }

  @Test
  void intercept_whenVmStatOutputsMalformedNumber_handlesGracefully() {
    try (org.mockito.MockedConstruction<ProcessBuilder> mocked =
        org.mockito.Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = org.mockito.Mockito.mock(Process.class);
              when(mockProcess.waitFor()).thenReturn(0);

              // "not-a-number" page count will cause NumberFormatException and return 0
              String output =
                  """
          Mach Virtual Memory Statistics: (page size of 4096 bytes)
          Pages free:                  not-a-number.
          Pages active:                10.
          Pages inactive:              10.
          Pages wired:                 10.
          Pages occupied by compressor: 10.
          """;

              java.io.ByteArrayInputStream inputStream =
                  new java.io.ByteArrayInputStream(output.getBytes());
              when(mockProcess.getInputStream()).thenReturn(inputStream);
              when(mock.start()).thenReturn(mockProcess);
            })) {
      var context = new PromptContext("test query", Map.of());
      PromptContext result = interceptor.intercept(context);

      // Total pages = 40 (excluding free). Used pages = 30. usage = 30/40 * 100 = 75%
      assertEquals(75.0, result.systemMetadata().get("memoryUsagePercent"));
    }
  }

  @Test
  void intercept_whenProcessInterrupted_returnsZeroAndRestoresInterruptStatus() {
    try (org.mockito.MockedConstruction<ProcessBuilder> mocked =
        org.mockito.Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = org.mockito.Mockito.mock(Process.class);
              try {
                when(mockProcess.waitFor()).thenThrow(new InterruptedException("interrupted"));
              } catch (InterruptedException e) {
                // Ignored in mock configuration
              }
              java.io.ByteArrayInputStream inputStream =
                  new java.io.ByteArrayInputStream("".getBytes());
              when(mockProcess.getInputStream()).thenReturn(inputStream);
              when(mock.start()).thenReturn(mockProcess);
            })) {
      Thread.currentThread().interrupt(); // Pre-interrupt the thread or mock waitFor to throw it
      var context = new PromptContext("test query", Map.of());
      PromptContext result = interceptor.intercept(context);

      assertEquals(0.0, result.systemMetadata().get("memoryUsagePercent"));
      assertTrue(Thread.currentThread().isInterrupted());
      // Clean up interrupt status
      Thread.interrupted();
    }
  }
}
