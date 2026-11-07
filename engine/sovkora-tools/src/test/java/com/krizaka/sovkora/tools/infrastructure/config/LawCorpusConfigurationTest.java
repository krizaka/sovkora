package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.application.service.LawArticleReadService;
import com.krizaka.sovkora.tools.application.service.LawManualImportService;
import com.krizaka.sovkora.tools.application.service.LawStatuteLookupService;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.infrastructure.adapter.mcp.LookupStatuteTool;
import com.krizaka.sovkora.tools.infrastructure.adapter.mcp.LookupStatuteToolRegistration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LawCorpusConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(LawCorpusConfiguration.class)
          .withBean(RuntimeConfigProvider.class, OffConfig::new)
          .withBean(LawCorpusStore.class, EmptyStore::new);

  /** The same wiring with the corpus switched on. */
  private final ApplicationContextRunner corpusOn =
      new ApplicationContextRunner()
          .withUserConfiguration(LawCorpusConfiguration.class)
          .withBean(RuntimeConfigProvider.class, OnConfig::new)
          .withBean(LawCorpusStore.class, EmptyStore::new);

  @Test
  void the_corpus_services_and_both_importers_are_wired_but_the_tool_is_not_offered() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(LawManualImportService.class);
          assertThat(context).hasSingleBean(LawStatuteLookupService.class);
          assertThat(context.getBeansOfType(LawSource.class).values())
              .extracting(LawSource::sourceKey)
              .containsExactlyInAnyOrder("donnees-quebec", "justice-laws");
          assertThat(context).doesNotHaveBean(LookupStatuteToolRegistration.class);
        });
  }

  @Test
  void with_the_property_the_tool_is_registered_in_the_one_registry() {
    RecordingRegistry registry = new RecordingRegistry();
    runner
        .withBean(ToolRegistry.class, () -> registry)
        .withPropertyValues("orazaka.law.lookup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(registry.names).containsExactly("lookupStatute");
              assertThat(registry.inputType).isEqualTo(LookupStatuteRequest.class);
            });
  }

  @Test
  void the_tool_answers_corpus_disabled_while_the_setting_is_off_and_invalid_for_a_bad_question() {
    runner.run(
        context -> {
          LookupStatuteTool tool =
              new LookupStatuteTool(context.getBean(LawStatuteLookupService.class));

          assertThat(
                  tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "1942", null, "fr"))
                      .outcome())
              .isEqualTo("LAW_CORPUS_DISABLED");
          assertThat(
                  tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "1942", "yesterday", "fr"))
                      .outcome())
              .isEqualTo("INVALID_REQUEST");
          assertThat(tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "", null, "fr")))
              .isEqualTo(LookupStatuteResponse.invalid());
        });
  }

  @Test
  void the_read_api_use_case_is_wired_and_with_no_meter_it_refuses_never_serves_unmetered() {
    LawArticleQuery query = new LawArticleQuery("QC", "CCQ-1991", "1942", "fr", "2025-06-01", null);
    corpusOn.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(LawArticleReadService.class).read(query, "actor").outcome())
              .isEqualTo(LawArticleRead.Outcome.LAW_METERING_UNAVAILABLE);
        });
  }

  @Test
  void the_read_api_use_case_meters_through_the_research_meter_bean_when_one_is_wired() {
    List<String> held = new ArrayList<>();
    ResearchMeter meter =
        (runId, actorId, providerKey) -> {
          held.add(providerKey);
          return new ResearchMeter.Hold() {
            @Override
            public void settle() {
              // intentionally empty: test double
            }

            @Override
            public void release(String reason) {
              // intentionally empty: test double
            }
          };
        };
    LawArticleQuery query = new LawArticleQuery("QC", "CCQ-1991", "1942", "fr", "2025-06-01", null);
    corpusOn
        .withBean(ResearchMeter.class, () -> meter)
        .run(
            context -> {
              assertThat(
                      context.getBean(LawArticleReadService.class).read(query, "actor").outcome())
                  .isEqualTo(LawArticleRead.Outcome.LAW_VERSION_UNKNOWN);
              assertThat(held).containsExactly("law-corpus");
            });
  }

  /** The runtime settings with the corpus switched on. */
  static final class OnConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return "law.corpus.enabled".equals(key) || defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }

  /** The runtime settings with no law row: everything off. */
  static final class OffConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }

  /** An empty corpus: the wiring is under test, not the storage. */
  static final class EmptyStore implements LawCorpusStore {
    @Override
    public UUID beginImport(LawImportStart start) {
      return UUID.randomUUID();
    }

    @Override
    public void finishImport(UUID importId, LawImportResult result) {
      // intentionally empty: test double
    }

    @Override
    public Optional<String> lastEtag(String sourceKey, String datasetUrl) {
      return Optional.empty();
    }

    @Override
    public List<LawArticleVersion> currentVersions(String jurisdiction, String instrument) {
      return List.of();
    }

    @Override
    public void add(UUID importId, String sourceKey, LawArticle article, Instant seenAt) {
      // intentionally empty: test double
    }

    @Override
    public void replace(UUID importId, LawArticle previous, LawArticle next, Instant seenAt) {
      // intentionally empty: test double
    }

    @Override
    public void close(LawArticle previous, LocalDate inForceTo) {
      // intentionally empty: test double
    }

    @Override
    public void touch(
        String jurisdiction, String instrument, Collection<LawArticle> articles, Instant seenAt) {
      // intentionally empty: test double
    }

    @Override
    public Optional<LawArticleVersion> inForceOn(
        String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
      return Optional.empty();
    }
  }

  private static final class RecordingRegistry implements ToolRegistry {
    private final List<String> names = new ArrayList<>();
    private Class<?> inputType;

    @Override
    public <I, O> void registerTool(
        String name,
        String description,
        Class<I> inputType,
        Function<I, O> function,
        java.util.Set<String> requiredRoles) {
      names.add(name);
      this.inputType = inputType;
      assertThat(description).contains("LAW_VERSION_UNKNOWN");
    }

    @Override
    public List<EngineTool> getRegisteredTools(
        com.krizaka.sovkora.core.domain.model.security.Actor actor) {
      return List.of();
    }

    @Override
    public EngineTool authorize(
        com.krizaka.sovkora.core.domain.model.security.Actor actor, String name) {
      throw new IllegalArgumentException(name);
    }
  }
}
