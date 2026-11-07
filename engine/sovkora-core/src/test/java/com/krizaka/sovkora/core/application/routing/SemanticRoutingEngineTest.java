package com.krizaka.sovkora.core.application.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.ClassificationResponse;
import com.krizaka.sovkora.core.domain.model.ConditionalRoute;
import com.krizaka.sovkora.core.domain.ports.outbound.SemanticClassifierPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticRoutingEngineTest {

  @Mock private SemanticClassifierPort classifierPort;

  private SemanticRoutingEngine engine;

  @BeforeEach
  void setUp() {
    engine = new SemanticRoutingEngine(classifierPort);
  }

  @Test
  void classify_callsClassifierPort() {
    ClassificationResponse expected = new ClassificationResponse(List.of());
    when(classifierPort.classify("hello")).thenReturn(expected);

    ClassificationResponse actual = engine.classify("hello");
    assertSame(expected, actual);
  }

  @Test
  void resolveInterceptors_noIntentsClassified_returnsEmptyList() {
    when(classifierPort.classify("hello")).thenReturn(new ClassificationResponse(List.of()));

    List<PromptContextInterceptor> result =
        engine.resolveInterceptors("hello", List.of(), Map.of());

    assertTrue(result.isEmpty());
  }

  @Test
  void resolveInterceptors_withMatchingIntentsAndRoutes_returnsInterceptors() {
    ClassificationResponse.ClassifiedIntent intent =
        new ClassificationResponse.ClassifiedIntent("video_gen", 0.90);
    when(classifierPort.classify("generate video"))
        .thenReturn(new ClassificationResponse(List.of(intent)));

    ConditionalRoute route = new ConditionalRoute("video_gen", List.of("MediaInterceptor"), 0.70);

    PromptContextInterceptor mockInterceptor = mock(PromptContextInterceptor.class);
    Map<String, PromptContextInterceptor> registry = Map.of("MediaInterceptor", mockInterceptor);

    List<PromptContextInterceptor> result =
        engine.resolveInterceptors("generate video", List.of(route), registry);

    assertEquals(1, result.size());
    assertSame(mockInterceptor, result.getFirst());
  }

  @Test
  void resolveInterceptors_matchingIntentBelowThreshold_returnsEmptyList() {
    ClassificationResponse.ClassifiedIntent intent =
        new ClassificationResponse.ClassifiedIntent("video_gen", 0.50);
    when(classifierPort.classify("generate video"))
        .thenReturn(new ClassificationResponse(List.of(intent)));

    ConditionalRoute route = new ConditionalRoute("video_gen", List.of("MediaInterceptor"), 0.70);

    PromptContextInterceptor mockInterceptor = mock(PromptContextInterceptor.class);
    Map<String, PromptContextInterceptor> registry = Map.of("MediaInterceptor", mockInterceptor);

    List<PromptContextInterceptor> result =
        engine.resolveInterceptors("generate video", List.of(route), registry);

    assertTrue(result.isEmpty());
  }

  @Test
  void resolveInterceptors_interceptorMissingInRegistry_skipsAndReturnsRemaining() {
    ClassificationResponse.ClassifiedIntent intent =
        new ClassificationResponse.ClassifiedIntent("video_gen", 0.90);
    when(classifierPort.classify("generate video"))
        .thenReturn(new ClassificationResponse(List.of(intent)));

    ConditionalRoute route =
        new ConditionalRoute(
            "video_gen", List.of("MediaInterceptor", "SystemContextInjector"), 0.70);

    PromptContextInterceptor mockInterceptor = mock(PromptContextInterceptor.class);
    // Registry contains SystemContextInjector, but missing MediaInterceptor
    Map<String, PromptContextInterceptor> registry =
        Map.of("SystemContextInjector", mockInterceptor);

    List<PromptContextInterceptor> result =
        engine.resolveInterceptors("generate video", List.of(route), registry);

    assertEquals(1, result.size());
    assertSame(mockInterceptor, result.getFirst());
  }
}
