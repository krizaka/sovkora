package com.krizaka.sovkora.core.application.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.RoutingPolicy;
import com.krizaka.sovkora.core.domain.model.StepModelCandidate;
import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.domain.model.StepRoutingRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.StepModelRecorder;
import com.krizaka.sovkora.core.domain.ports.outbound.StepRouteProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PolicyStepRouterTest {

  @ParameterizedTest
  @EnumSource(RoutingPolicy.class)
  void usesEffectivePolicyAndDeclaredOrder(RoutingPolicy policy) {
    StepRouteProvider provider = mock(StepRouteProvider.class);
    when(provider.candidates("chat", policy))
        .thenReturn(
            List.of(
                new StepModelCandidate("first", true, true),
                new StepModelCandidate("second", true, true)));
    List<String> events = new ArrayList<>();
    PolicyStepRouter router = new PolicyStepRouter(provider);

    int result =
        router.execute(
            new StepRoutingRequest("step", "chat", policy),
            (step, model) -> events.add("record:" + step + ":" + model),
            model -> {
              events.add("invoke:" + model);
              return 42;
            });

    assertEquals(42, result);
    assertEquals(List.of("record:step:first", "invoke:first"), events);
    verify(provider).candidates("chat", policy);
    verifyNoMoreInteractions(provider);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoutingPolicy.class,
      names = {"COST_OPTIMIZED", "LATENCY_FIRST"})
  void explicitNonSovereignPoliciesPermitHostedModels(RoutingPolicy policy) {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, effectivePolicy) ->
                List.of(new StepModelCandidate("hosted", false, true)));
    List<String> recorded = new ArrayList<>();

    assertEquals(
        "hosted",
        router.execute(
            new StepRoutingRequest("step", "chat", policy),
            (step, model) -> recorded.add(model),
            model -> model));
    assertEquals(List.of("hosted"), recorded);
  }

  @Test
  void nullPolicyRejectsHostedAndUnknownEvenWhenNamesLookLocal() {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, policy) -> {
              assertEquals(RoutingPolicy.SOVEREIGN_LOCAL_ONLY, policy);
              return List.of(
                  new StepModelCandidate("hosted", false, true),
                  new StepModelCandidate("ollama-unknown", false, true),
                  new StepModelCandidate("sovereign", true, true));
            });
    List<String> recorded = new ArrayList<>();

    assertEquals(
        "sovereign",
        router.execute(
            new StepRoutingRequest("step", "chat", null),
            (step, model) -> recorded.add(model),
            model -> model));
    assertEquals(List.of("sovereign"), recorded);
  }

  @Test
  void fallsBackInOrderAndRecordsSecondaryBeforeInvokingIt() {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, policy) ->
                List.of(
                    new StepModelCandidate("offline", true, false),
                    new StepModelCandidate("primary", true, true),
                    new StepModelCandidate("secondary", true, true),
                    new StepModelCandidate("unused", true, true)));
    List<String> events = new ArrayList<>();

    String result =
        router.execute(
            new StepRoutingRequest("step-42", "chat"),
            (step, model) -> events.add("record:" + step + ":" + model),
            model -> {
              events.add("invoke:" + model);
              if ("primary".equals(model)) {
                throw new StepModelUnavailableException("primary offline");
              }
              return model;
            });

    assertEquals("secondary", result);
    assertEquals(
        List.of(
            "record:step-42:primary",
            "invoke:primary",
            "record:step-42:secondary",
            "invoke:secondary"),
        events);
  }

  @Test
  void exhaustionRetainsLastFailureAndNeverUsesUndeclaredModels() {
    StepRouteProvider provider = mock(StepRouteProvider.class);
    when(provider.candidates("chat", RoutingPolicy.SOVEREIGN_LOCAL_ONLY))
        .thenReturn(
            List.of(
                new StepModelCandidate("primary", true, true),
                new StepModelCandidate("secondary", true, true)));
    StepModelUnavailableException first = new StepModelUnavailableException("first");
    StepModelUnavailableException last =
        new StepModelUnavailableException("last", new IllegalStateException("offline"));
    List<String> invoked = new ArrayList<>();
    List<String> recorded = new ArrayList<>();
    PolicyStepRouter router = new PolicyStepRouter(provider);

    StepModelUnavailableException failure =
        assertThrows(
            StepModelUnavailableException.class,
            () ->
                router.execute(
                    new StepRoutingRequest("step", "chat"),
                    (step, model) -> recorded.add(model),
                    model -> {
                      invoked.add(model);
                      throw "primary".equals(model) ? first : last;
                    }));

    assertSame(last, failure.getCause());
    assertEquals(List.of("primary", "secondary"), invoked);
    assertEquals(invoked, recorded);
    verify(provider).candidates("chat", RoutingPolicy.SOVEREIGN_LOCAL_ONLY);
    verifyNoMoreInteractions(provider);
  }

  @Test
  void missingOrIneligibleConfigurationFailsClosedWithoutInvocationOrRecording() {
    List<List<StepModelCandidate>> configurations = new ArrayList<>();
    configurations.add(null);
    configurations.add(List.of());
    configurations.add(
        Arrays.asList(
            null,
            new StepModelCandidate("hosted", false, true),
            new StepModelCandidate("unknown", false, true),
            new StepModelCandidate("offline", true, false)));
    List<String> events = new ArrayList<>();

    for (List<StepModelCandidate> candidates : configurations) {
      PolicyStepRouter router = new PolicyStepRouter((capability, policy) -> candidates);
      StepModelUnavailableException failure =
          assertThrows(
              StepModelUnavailableException.class,
              () ->
                  router.execute(
                      new StepRoutingRequest("step", "chat"),
                      (step, model) -> events.add("record:" + model),
                      model -> events.add("invoke:" + model)));
      assertNull(failure.getCause());
    }
    assertEquals(List.of(), events);
  }

  @Test
  void nonAvailabilityInvocationFailuresPropagateWithoutFallback() {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, policy) ->
                List.of(
                    new StepModelCandidate("primary", true, true),
                    new StepModelCandidate("secondary", true, true)));

    for (RuntimeException original :
        List.of(
            new IllegalStateException("billing denied"),
            new SecurityException("safety denied"),
            new IllegalArgumentException("invalid request"))) {
      List<String> events = new ArrayList<>();
      RuntimeException failure =
          assertThrows(
              RuntimeException.class,
              () ->
                  router.execute(
                      new StepRoutingRequest("step", "chat"),
                      (step, model) -> events.add("record:" + model),
                      model -> {
                        events.add("invoke:" + model);
                        throw original;
                      }));
      assertSame(original, failure);
      assertEquals(List.of("record:primary", "invoke:primary"), events);
    }
  }

  @Test
  void recorderFailureNeverInvokesOrFallsBackEvenForUnavailableException() {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, policy) ->
                List.of(
                    new StepModelCandidate("primary", true, true),
                    new StepModelCandidate("secondary", true, true)));

    for (RuntimeException original :
        List.of(
            new IllegalStateException("persistence failed"),
            new StepModelUnavailableException("recorder failed"))) {
      List<String> events = new ArrayList<>();
      RuntimeException failure =
          assertThrows(
              RuntimeException.class,
              () ->
                  router.execute(
                      new StepRoutingRequest("step", "chat"),
                      (step, model) -> {
                        events.add("record:" + model);
                        throw original;
                      },
                      model -> events.add("invoke:" + model)));
      assertSame(original, failure);
      assertEquals(List.of("record:primary"), events);
    }
  }

  @Test
  void readsConfigurationOnceOnEveryExecution() {
    StepRouteProvider provider = mock(StepRouteProvider.class);
    when(provider.candidates("chat", RoutingPolicy.SOVEREIGN_LOCAL_ONLY))
        .thenReturn(List.of(new StepModelCandidate("old", true, true)))
        .thenReturn(List.of(new StepModelCandidate("new", true, true)));
    PolicyStepRouter router = new PolicyStepRouter(provider);
    StepRoutingRequest request = new StepRoutingRequest("step", "chat");
    List<String> recorded = new ArrayList<>();
    StepModelRecorder recorder = (step, model) -> recorded.add(model);

    assertEquals("old", router.execute(request, recorder, model -> model));
    assertEquals("new", router.execute(request, recorder, model -> model));
    assertEquals(List.of("old", "new"), recorded);
    verify(provider, org.mockito.Mockito.times(2))
        .candidates("chat", RoutingPolicy.SOVEREIGN_LOCAL_ONLY);
    verifyNoMoreInteractions(provider);
  }

  @Test
  void missingMandatoryRecorderFailsBeforeReadingRoutes() {
    StepRouteProvider provider = mock(StepRouteProvider.class);
    PolicyStepRouter router = new PolicyStepRouter(provider);

    assertThrows(
        NullPointerException.class,
        () -> router.execute(new StepRoutingRequest("step", "chat"), null, model -> model));
    verifyNoMoreInteractions(provider);
  }

  @Test
  void successfulNullResultDoesNotFallBack() {
    PolicyStepRouter router =
        new PolicyStepRouter(
            (capability, policy) ->
                List.of(
                    new StepModelCandidate("primary", true, true),
                    new StepModelCandidate("secondary", true, true)));
    List<String> recorded = new ArrayList<>();

    assertNull(
        router.execute(
            new StepRoutingRequest("step", "chat"),
            (step, model) -> recorded.add(model),
            model -> null));
    assertEquals(List.of("primary"), recorded);
  }
}
