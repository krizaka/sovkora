package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.*;
import com.krizaka.sovkora.tools.application.service.LawArticleReadService;
import com.krizaka.sovkora.tools.application.service.LawImportService;
import com.krizaka.sovkora.tools.application.service.LawManualImportService;
import com.krizaka.sovkora.tools.application.service.LawStatuteLookupService;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.JusticeLawsXmlSource;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawXmlFileReader;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.QuebecOpenDataSource;
import com.krizaka.sovkora.tools.infrastructure.adapter.mcp.LookupStatuteTool;
import com.krizaka.sovkora.tools.infrastructure.adapter.mcp.LookupStatuteToolRegistration;
import com.krizaka.sovkora.tools.infrastructure.law.RuntimeConfigLawSettings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Wires the law corpus (ADR-089 §2): the {@code law.*} settings, the importers of the official
 * sources, the shared import, the manual import, {@code lookupStatute} and the read API's use case.
 * Nothing here reads the network or writes the corpus until a caller asks, and every call checks
 * {@code law.corpus.enabled}. The scheduled watcher is {@link LawWatchConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LawWatchProperties.class)
public class LawCorpusConfiguration {

  /** Identifies the importers to the publishers, as ADR-087 asks: never a browser disguise. */
  static final String USER_AGENT = "SovkoraLawWatcher/0.1 (+https://www.sovkora.com)";

  @Bean
  LawSettings lawSettings(RuntimeConfigProvider config) {
    return new RuntimeConfigLawSettings(config);
  }

  @Bean
  LawStatuteXmlParser lawStatuteXmlParser() {
    return new LawStatuteXmlParser();
  }

  @Bean
  LawFileReader lawFileReader(LawStatuteXmlParser parser) {
    return new LawXmlFileReader(parser);
  }

  @Bean
  LawSource quebecOpenDataSource(LawWatchProperties properties, LawStatuteXmlParser parser) {
    return new QuebecOpenDataSource(openDataClient(), properties.quebec().datasetUrl(), parser);
  }

  @Bean
  LawSource justiceLawsXmlSource(LawStatuteXmlParser parser) {
    return new JusticeLawsXmlSource(openDataClient(), parser);
  }

  private static RestClient openDataClient() {
    return RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
  }

  @Bean
  LawImportService lawImportService(
      LawCorpusStore store, LawSettings settings, ObjectProvider<LawChangeRequests> requests) {
    return new LawImportService(store, settings, requests.getIfAvailable(), Clock.systemUTC());
  }

  @Bean
  LawManualImportService lawManualImportService(
      List<LawSource> sources,
      LawSettings settings,
      LawFileReader reader,
      LawImportService importer) {
    return new LawManualImportService(sources, settings, reader, importer);
  }

  @Bean
  LawStatuteLookupService lawStatuteLookupService(LawCorpusStore store, LawSettings settings) {
    return new LawStatuteLookupService(store, settings, Clock.systemUTC());
  }

  /**
   * The read API's use case (issue #89). The meter is the host's {@link ResearchMeter} bean; with
   * none, every read is refused ({@code LAW_METERING_UNAVAILABLE}), never served unmetered.
   */
  @Bean
  LawArticleReadService lawArticleReadService(
      LawStatuteLookupService lookups, LawSettings settings, ObjectProvider<ResearchMeter> meter) {
    return new LawArticleReadService(lookups, settings, meter.getIfAvailable());
  }

  @Bean
  @ConditionalOnProperty(prefix = "orazaka.law.lookup", name = "enabled", havingValue = "true")
  LookupStatuteToolRegistration lookupStatuteToolRegistration(
      ToolRegistry registry, LawStatuteLookupService lookups) {
    return new LookupStatuteToolRegistration(registry, new LookupStatuteTool(lookups));
  }
}
