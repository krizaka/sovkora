package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.GroundedResearchSettings;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.application.service.GroundedResearchService;
import com.krizaka.sovkora.tools.application.service.LawCorpusResearchService;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.ChatGroundedClaimsModel;
import com.krizaka.sovkora.tools.infrastructure.law.RuntimeConfigGroundedResearchSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@code research.grounded} capability (ADR-087 slice 5) on top of {@link
 * LawCorpusConfiguration}. Off by default: {@code orazaka.law.grounded.enabled=true} registers
 * {@link GroundedResearchService}, which the job service's executor for the handler key {@code
 * research.grounded} calls. Even then every call is refused while {@code research.enabled}, {@code
 * law.corpus.enabled} or the {@code law-corpus} source is off, and a call with a document while
 * {@code law.lecture.enabled} is off (all seeded off).
 *
 * <p>It fails closed: without a metering bean a call is refused before anything is asked.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orazaka.law.grounded", name = "enabled", havingValue = "true")
public class GroundedResearchConfiguration {

  @Bean
  GroundedResearchSettings groundedResearchSettings(RuntimeConfigProvider config) {
    return new RuntimeConfigGroundedResearchSettings(config);
  }

  @Bean
  Collaborators groundedCollaborators(
      ObjectProvider<GroundedClaimsModel> model,
      AiClient chat,
      ObjectProvider<ResearchMeter> meter,
      ObjectProvider<ChatTokenMeter> tokenMeter,
      LawCorpusStore corpus,
      ResearchStore research) {
    return new Collaborators(
        new LawCorpusResearchService(corpus, research, Clock.systemUTC()),
        model.getIfAvailable(() -> new ChatGroundedClaimsModel(chat)),
        meter.getIfAvailable(GroundedResearchConfiguration::noMeter),
        tokenMeter.getIfAvailable(GroundedResearchConfiguration::noTokenMeter));
  }

  @Bean
  GroundedResearchService groundedResearchService(
      GroundedResearchSettings settings,
      ResearchStore research,
      CitationVerifier verifier,
      Collaborators collaborators) {
    return new GroundedResearchService(
        settings,
        research,
        collaborators.provider(),
        collaborators.model(),
        verifier,
        collaborators.meter(),
        collaborators.tokenMeter());
  }

  /** The corpus provider, the model and the two meters, resolved once (fail closed). */
  record Collaborators(
      LawCorpusResearchService provider,
      GroundedClaimsModel model,
      ResearchMeter meter,
      ChatTokenMeter tokenMeter) {}

  private static ResearchMeter noMeter() {
    return (runId, actorId, providerKey) -> {
      throw new ResearchMeter.BudgetExceededException(
          "no metering is wired: a grounded call is never made unmetered");
    };
  }

  private static ChatTokenMeter noTokenMeter() {
    return (runId, actorId) -> {
      throw new ResearchMeter.BudgetExceededException(
          "no token metering is wired: a grounded call is never made unmetered");
    };
  }
}
