package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.application.service.LawCorpusResearchService;
import com.krizaka.sovkora.tools.application.service.LawRetrievalService;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the retrieval of the law through grounded research (ADR-089 §2) on top of {@link
 * LawCorpusConfiguration}. Off by default: {@code orazaka.law.retrieval.enabled=true} registers it,
 * and even then every call is refused while {@code law.corpus.enabled}, {@code research.enabled} or
 * the {@code law-corpus} source is off.
 *
 * <p>It fails closed. The model of the grounded call and the metering are supplied by the
 * grounded-research capability (ADR-087); until they are wired a call is refused before anything is
 * asked, never made unmetered.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orazaka.law.retrieval", name = "enabled", havingValue = "true")
public class LawRetrievalConfiguration {

  @Bean
  LawCorpusResearchService lawCorpusResearchProvider(
      LawCorpusStore corpus, ResearchStore research) {
    return new LawCorpusResearchService(corpus, research, Clock.systemUTC());
  }

  @Bean
  LawRetrievalService lawRetrievalService(
      LawSettings settings,
      ResearchStore research,
      LawCorpusResearchService provider,
      LawRetrievalCache cache,
      CitationVerifier verifier,
      ObjectProvider<GroundedClaimsModel> model,
      ObjectProvider<ResearchMeter> meter) {
    return new LawRetrievalService(
        settings,
        research,
        provider,
        cache,
        model.getIfAvailable(LawRetrievalConfiguration::noModel),
        verifier,
        meter.getIfAvailable(LawRetrievalConfiguration::noMeter),
        Clock.systemUTC());
  }

  private static GroundedClaimsModel noModel() {
    return (question, language, passages) -> {
      throw new IllegalStateException("no grounded model is wired");
    };
  }

  private static ResearchMeter noMeter() {
    return (runId, actorId, providerKey) -> {
      throw new ResearchMeter.BudgetExceededException(
          "no metering is wired: a grounded call is never made unmetered");
    };
  }
}
