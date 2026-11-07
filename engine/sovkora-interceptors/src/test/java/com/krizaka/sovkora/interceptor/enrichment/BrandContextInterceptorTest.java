package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandContextInterceptorTest {

  private final BrandContextInterceptor interceptor = new BrandContextInterceptor();

  private static InternalChatRequest requestWith(Map<String, Object> preferences) {
    return new InternalChatRequest(
        "Décris cette pièce",
        List.of(),
        null,
        new Context("actor-1", "conv-1", preferences, Set.of()),
        false);
  }

  private static List<EngineMessage> messages() {
    List<EngineMessage> messages = new ArrayList<>();
    messages.add(EngineMessage.user("Décris cette pièce"));
    return messages;
  }

  @Test
  @DisplayName("A Studio step gets its brand kit prepended as a system message")
  void injectsTheBrandKit() {
    Map<String, Object> preferences = new LinkedHashMap<>();
    // The producer declares the namespace; the engine names none (ADR-050).
    preferences.put(JobCommand.ENRICHMENT_NAMESPACE_KEY, "orazaka.studio.brand.");
    preferences.put("orazaka.studio.brand.tone", "premium");
    preferences.put("orazaka.studio.brand.brandName", "Dupont & Fils");
    List<EngineMessage> messages = messages();

    interceptor.preProcess(requestWith(preferences), "Décris cette pièce", messages, null);

    assertThat(messages).hasSize(2);
    assertThat(messages.getFirst().text())
        .contains("Brand context")
        .contains("tone: premium")
        .contains("brandName: Dupont & Fils");
  }

  @Test
  @DisplayName("Ordinary chat is untouched — no namespace, no message")
  void noOpForOrdinaryChat() {
    List<EngineMessage> messages = messages();

    interceptor.preProcess(requestWith(Map.of("theme", "dark")), "Bonjour", messages, null);

    assertThat(messages).hasSize(1);
  }

  @Test
  @DisplayName("Only the brand namespace is lifted — a profile preference is not brand context")
  void liftsOnlyTheBrandNamespace() {
    Map<String, Object> preferences = new LinkedHashMap<>();
    preferences.put(JobCommand.ENRICHMENT_NAMESPACE_KEY, "orazaka.studio.brand.");
    preferences.put("orazaka.studio.brand.tone", "premium");
    preferences.put("voiceModel", "alloy");
    List<EngineMessage> messages = messages();

    interceptor.preProcess(requestWith(preferences), "x", messages, null);

    assertThat(messages.getFirst().text()).contains("tone: premium").doesNotContain("alloy");
  }

  @Test
  @DisplayName("The rendered block is stable across runs, so identical runs stay comparable")
  void renderingIsStable() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put(JobCommand.ENRICHMENT_NAMESPACE_KEY, "orazaka.studio.brand.");
    first.put("orazaka.studio.brand.tone", "premium");
    first.put("orazaka.studio.brand.brandName", "Dupont");
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("orazaka.studio.brand.brandName", "Dupont");
    second.put(JobCommand.ENRICHMENT_NAMESPACE_KEY, "orazaka.studio.brand.");
    second.put("orazaka.studio.brand.tone", "premium");

    List<EngineMessage> a = messages();
    List<EngineMessage> b = messages();
    interceptor.preProcess(requestWith(first), "x", a, null);
    interceptor.preProcess(requestWith(second), "x", b, null);

    assertThat(a.getFirst().text()).isEqualTo(b.getFirst().text());
  }

  @Test
  @DisplayName("[ADR-050] a pack the engine never heard of enriches its OWN namespace")
  void anExternalPackBringsItsOwnContext() {
    // Nothing in the engine names "orazaka.acme.voice.". This is the criterion the phase-G
    // finding asked for: an external pack has a context without an engine edit.
    Map<String, Object> preferences = new LinkedHashMap<>();
    preferences.put(JobCommand.ENRICHMENT_NAMESPACE_KEY, "orazaka.acme.voice.");
    preferences.put("orazaka.acme.voice.register", "formel");
    preferences.put("orazaka.studio.brand.tone", "premium");
    List<EngineMessage> messages = messages();

    interceptor.preProcess(requestWith(preferences), "x", messages, null);

    assertThat(messages.getFirst().text()).contains("register: formel").doesNotContain("premium");
  }

  @Test
  @DisplayName("[ADR-050] preferences with no declared namespace enrich nothing")
  void noDeclarationNoEnrichment() {
    // The Studio's own namespace, undeclared. Enriching it anyway would be the engine assuming a
    // default, which is how it came to know one product's vocabulary in the first place.
    List<EngineMessage> messages = messages();

    interceptor.preProcess(
        requestWith(Map.of("orazaka.studio.brand.tone", "premium")), "x", messages, null);

    assertThat(messages).hasSize(1);
  }

  @Test
  @DisplayName("It survives the disable-ai kill-switch: pure enrichment, no model call")
  void isNotAiDependent() {
    assertThat(interceptor.isAiDependent()).isFalse();
  }

  @Test
  @DisplayName("A request with no context is tolerated rather than throwing mid-pipeline")
  void toleratesAMissingContext() {
    List<EngineMessage> messages = messages();

    interceptor.preProcess(
        new InternalChatRequest("x", List.of(), null, null, false), "x", messages, null);

    assertThat(messages).hasSize(1);
  }
}
