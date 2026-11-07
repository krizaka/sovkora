package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.adapter.springai.tool.SpringAiToolRegistry;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.sovkora.tools.infrastructure.cache.ToolCacheStore;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultToolCatalogueTest {

  private static final Actor USER = Actor.of("user-123", List.of("ROLE_USER")).orElseThrow();
  private static final Actor HR = Actor.of("hr-1", List.of("ROLE_USER", "ROLE_HR")).orElseThrow();
  private static final Actor ADMIN = Actor.of("root", List.of("ROLE_ADMIN")).orElseThrow();

  @Mock private PlatformToolConfigProvider platformToolConfigProvider;

  @Mock private ToolCacheStore cacheService;

  @Mock private KnowledgeService knowledgeService;

  @Mock private ToolPermissionProvider permissions;

  private SpringAiToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SpringAiToolRegistry(platformToolConfigProvider, cacheService, permissions);
    new DefaultToolCatalogue(registry, knowledgeService).registerDefaultTools();
  }

  private EngineTool tool(Actor actor, String name) {
    return registry.getRegisteredTools(actor).stream()
        .filter(t -> name.equals(t.name()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void theCorpusSearchIsNamedForWhatItDoes() {
    // [#59] the corpus search says what it is: knowledge, not the web (ADR-087).
    var tool = tool(USER, "searchKnowledge");
    assertThat(tool.description())
        .containsIgnoringCase("knowledge")
        .containsIgnoringCase("not the web")
        .doesNotContainIgnoringCase("real-time");
    assertThat(registry.getRegisteredTools(USER)).noneMatch(t -> "searchWeb".equals(t.name()));
  }

  @Test
  @DisplayName("a tool that declares nothing is for administrators only")
  void shouldRegisterToolWithLeastPrivilege() {
    registry.registerTool("testTool", "A test tool", String.class, (String s) -> "Hello " + s);

    assertThat(names(registry.getRegisteredTools(USER)))
        .containsExactlyInAnyOrder("analyzePoster", "analyzeAudioExtract", "searchKnowledge");
    assertThat(names(registry.getRegisteredTools(ADMIN))).containsExactlyInAnyOrder("testTool");
  }

  @Test
  @DisplayName("a tool that declares its roles is offered to the holders of one of them")
  void declaredRolesAreHonoured() {
    Function<String, String> function = input -> "payroll " + input;
    registry.registerTool("payroll", "Payroll", String.class, function, Set.of("ROLE_HR"));

    assertThat(names(registry.getRegisteredTools(USER))).doesNotContain("payroll");
    assertThat(names(registry.getRegisteredTools(HR))).contains("payroll");
  }

  @Test
  @DisplayName("an administrator's rows replace what the tool declared, on the next call")
  void configuredRolesOverrideTheDeclaredOnes() {
    when(permissions.requiredRoles("analyzePoster")).thenReturn(Optional.of(Set.of("ROLE_HR")));

    assertThat(names(registry.getRegisteredTools(USER))).doesNotContain("analyzePoster");
    assertThat(names(registry.getRegisteredTools(HR))).contains("analyzePoster");
    assertThatThrownBy(() -> registry.authorize(USER, "analyzePoster"))
        .isInstanceOf(AccessDeniedException.class)
        .extracting(e -> ((AccessDeniedException) e).reason())
        .isEqualTo(Reason.MISSING_ROLE);
    assertThat(registry.authorize(HR, "analyzePoster")).isNotNull();
  }

  @Test
  @DisplayName(
      "authorizing refuses when no actor is present, and an empty requirement admits nobody")
  void failsClosed() {
    assertThatThrownBy(() -> registry.authorize(null, "searchKnowledge"))
        .isInstanceOf(AccessDeniedException.class)
        .extracting(e -> ((AccessDeniedException) e).reason())
        .isEqualTo(Reason.NO_ACTOR);
    assertThat(registry.getRegisteredTools(null)).isEmpty();

    registry.registerTool("sealed", "Nobody", String.class, (String s) -> s, Set.of());
    assertThatThrownBy(() -> registry.authorize(ADMIN, "sealed"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> registry.authorize(USER, "nonexistent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool not found: nonexistent");
  }

  @ParameterizedTest
  @CsvSource({
    "analyzePoster, '{\"posterBase64\":\"valid-base64\",\"prompt\":\"cat\"}', 'Poster analysis success'",
    "analyzePoster, '{\"posterBase64\":\"corrupt-data\",\"prompt\":\"cat\"}', 'Failed to parse poster image'",
    "analyzeAudioExtract, '{\"clipPath\":\"valid-path.mp3\",\"checkType\":\"loudness\"}', 'Audio extract compliance check passed'",
    "analyzeAudioExtract, '{\"clipPath\":\"corrupt-path.mp3\",\"checkType\":\"loudness\"}', 'Audio clip corrupt or missing'"
  })
  void toolCallbacks(String toolName, String inputJson, String expectedSubstring) {
    String responseJson = tool(USER, toolName).call(inputJson);
    assertNotNull(responseJson);
    assertTrue(responseJson.contains(expectedSubstring));
  }

  @Test
  void searchKnowledge_knowledgeServiceNull_returnsErrorResponse() {
    SpringAiToolRegistry registryNoKnowledge = new SpringAiToolRegistry(null, null, null);
    new DefaultToolCatalogue(registryNoKnowledge, null).registerDefaultTools();
    EngineTool tool =
        registryNoKnowledge.getRegisteredTools(USER).stream()
            .filter(t -> "searchKnowledge".equals(t.name()))
            .findFirst()
            .orElseThrow();

    String response = tool.call("{\"query\":\"test\"}");
    assertTrue(response.contains("Knowledge service is not initialized"));
  }

  @Test
  @DisplayName("the knowledge is searched for the actor the tool runs for")
  void searchKnowledge_withMatches_searchesForTheBoundActor() {
    when(knowledgeService.searchSources(USER, "searchKnowledge", "Corporate"))
        .thenReturn("Corporate description profile content.");

    String response = tool(USER, "searchKnowledge").call("{\"query\":\"Corporate\"}");

    assertTrue(response.contains("Corporate description profile content"));
  }

  @Test
  @DisplayName("two actors never share a search: each call carries its own actor")
  void searchKnowledge_isPerActor() {
    when(knowledgeService.searchSources(HR, "searchKnowledge", "salary")).thenReturn("480000");
    when(knowledgeService.searchSources(USER, "searchKnowledge", "salary")).thenReturn("");
    when(knowledgeService.searchSources(USER, "searchKnowledge", "")).thenReturn("");

    assertTrue(tool(HR, "searchKnowledge").call("{\"query\":\"salary\"}").contains("480000"));
    assertThat(tool(USER, "searchKnowledge").call("{\"query\":\"salary\"}"))
        .doesNotContain("480000");
  }

  @Test
  void searchKnowledge_noMatch_fallsBackToAllVisibleSources() {
    when(knowledgeService.searchSources(USER, "searchKnowledge", "Corporate")).thenReturn("");
    when(knowledgeService.searchSources(USER, "searchKnowledge", ""))
        .thenReturn("Corporate fallback profile.");

    String response = tool(USER, "searchKnowledge").call("{\"query\":\"Corporate\"}");
    assertTrue(response.contains("Corporate fallback profile"));
  }

  @Test
  @DisplayName("a knowledge tool run outside any actor scope reads nothing")
  void searchKnowledge_withoutAnActorReadsNothing() {
    var response =
        new DefaultToolCatalogue(registry, knowledgeService)
            .searchKnowledge(
                new com.krizaka.sovkora.tools.domain.model.search.SearchWebRequest("salary"));

    assertThat(response.success()).isFalse();
    assertThat(response.toString()).doesNotContain("480000");
    verifyNoInteractions(knowledgeService);
  }

  @Test
  @DisplayName("a cached tool result is keyed by the actor's scope, so another actor misses")
  void getRegisteredTools_cacheKeyCarriesTheActorScope() {
    PlatformToolConfigProvider.PlatformToolConfig config =
        new PlatformToolConfigProvider.PlatformToolConfig(
            1, "analyzePoster", true, 600, false, null, null);
    when(platformToolConfigProvider.getToolConfig("analyzePoster")).thenReturn(Optional.of(config));
    String input = "{\"posterBase64\":\"valid-base64\",\"prompt\":\"cat\"}";

    tool(USER, "analyzePoster").call(input);
    tool(HR, "analyzePoster").call(input);

    verify(cacheService).get("analyzePoster", USER.scope() + "\n" + input);
    verify(cacheService).get("analyzePoster", HR.scope() + "\n" + input);
    assertThat(USER.scope()).isNotEqualTo(HR.scope());
  }

  private static List<String> names(List<EngineTool> tools) {
    return tools.stream().map(EngineTool::name).toList();
  }
}
