package com.krizaka.sovkora.core.infrastructure.adapter.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.routing.PolicyStepRouter;
import com.krizaka.sovkora.core.domain.model.RoutingPolicy;
import com.krizaka.sovkora.core.domain.model.StepModelCandidate;
import com.krizaka.sovkora.core.domain.model.StepRoutingRequest;
import com.krizaka.sovkora.core.infrastructure.provider.model.ModelUnavailableException;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.ObjectMapper;

class RuntimeStepRouteProviderTest {

  private final RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
  private final CatalogModelManager catalog = mock(CatalogModelManager.class);
  private final RuntimeStepRouteProvider provider =
      new RuntimeStepRouteProvider(config, catalog, new ObjectMapper());

  @ParameterizedTest
  @EnumSource(RoutingPolicy.class)
  void readsOnlyTheDeclaredPolicyInAdminOrder(RoutingPolicy policy) {
    when(config.getString("routing.text.generate." + policy.name(), null))
        .thenReturn("[\"secondary\", \"primary\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("primary", "local", "available"),
                model("secondary", "provider", "available")));

    var routes = provider.candidates("text.generate", policy);

    assertEquals(
        List.of("secondary", "primary"),
        routes.stream().map(StepModelCandidate::modelName).toList());
    assertFalse(routes.getFirst().sovereignLocal());
    assertTrue(routes.getLast().sovereignLocal());
  }

  @Test
  void missingPolicyDoesNotUseTheCatalogDefault() {
    assertTrue(provider.candidates("text.generate", RoutingPolicy.SOVEREIGN_LOCAL_ONLY).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(RoutingPolicy.class)
  void sdkProviderFailureUsesDeclaredSecondaryAndRecordsItsName(RoutingPolicy policy) {
    when(config.getString("routing.text.generate." + policy.name(), null))
        .thenReturn("[\"primary\",\"secondary\",\"hosted\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("primary", "local", "available"),
                model("secondary", "local", "available"),
                model("hosted", "provider", "available")));
    var primary = mock(ChatModel.class);
    var secondary = mock(ChatModel.class);
    var response = mock(ChatResponse.class);
    var models = Map.of("primary", primary, "secondary", secondary);
    var asked = new ArrayList<String>();
    when(primary.call(any(Prompt.class)))
        .thenThrow(
            InternalServerException.builder()
                .statusCode(503)
                .headers(Headers.builder().build())
                .build());
    when(secondary.call(any(Prompt.class))).thenReturn(response);
    var selectedModels = new HashMap<String, String>();

    var result =
        new PolicyStepRouter(provider)
            .execute(
                new StepRoutingRequest("step-42", "text.generate", policy),
                selectedModels::put,
                name -> {
                  asked.add(name);
                  try {
                    return models
                        .get(name)
                        .call(new Prompt("hi", ChatOptions.builder().model(name).build()));
                  } catch (RuntimeException e) {
                    // what the adapter's RoutedChatModel does with an unreachable provider
                    throw new ModelUnavailableException(name, "provider", e);
                  }
                });

    assertEquals(response, result);
    assertEquals("secondary", selectedModels.get("step-42"));
    assertEquals(List.of("primary", "secondary"), asked);
  }

  @Test
  void undeclaredLocationAndUnavailableOrMissingModelsFailClosed() {
    when(config.getString("routing.text.generate.SOVEREIGN_LOCAL_ONLY", null))
        .thenReturn("[\"unknown-location\", \"planned\", \"missing\"]");
    when(catalog.getAllModels())
        .thenReturn(
            List.of(
                model("unknown-location", null, "available"),
                model("planned", "local", "planned")));

    var routes = provider.candidates("text.generate", RoutingPolicy.SOVEREIGN_LOCAL_ONLY);

    assertFalse(routes.getFirst().sovereignLocal());
    assertFalse(routes.get(1).available());
    assertFalse(routes.getLast().available());
  }

  @Test
  void adminEditsAreReadOnTheNextInvocation() {
    when(config.getString("routing.text.generate.COST_OPTIMIZED", null))
        .thenReturn("[\"primary\"]", "[\"secondary\", \"primary\"]");
    when(catalog.getAllModels())
        .thenReturn(List.of(model("primary", "local", "available")))
        .thenReturn(
            List.of(
                model("primary", "provider", "available"),
                model("secondary", "local", "available")));

    assertTrue(
        provider
            .candidates("text.generate", RoutingPolicy.COST_OPTIMIZED)
            .getFirst()
            .sovereignLocal());
    var updated = provider.candidates("text.generate", RoutingPolicy.COST_OPTIMIZED);
    assertEquals("secondary", updated.getFirst().modelName());
    assertFalse(updated.getLast().sovereignLocal());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "null", "{}", "[1]", "[null]", "[\"\"]", "[\"a\", \"a\"]"})
  void invalidDeclarationsAreRejected(String declaration) {
    when(config.getString("routing.text.generate.LATENCY_FIRST", null)).thenReturn(declaration);

    assertThrows(
        IllegalStateException.class,
        () -> provider.candidates("text.generate", RoutingPolicy.LATENCY_FIRST));
  }

  /** A real catalogue row: the DTO is a record, and a record is final — it cannot be mocked. */
  private static CatalogModelDto model(String name, String runsOn, String status) {
    return new CatalogModelDto(
        null, name, name, "text", null, false, null, null, null, null, null, runsOn, status, null,
        null);
  }
}
