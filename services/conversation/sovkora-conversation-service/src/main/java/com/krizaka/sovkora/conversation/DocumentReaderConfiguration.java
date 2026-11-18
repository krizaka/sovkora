package com.krizaka.sovkora.conversation;

import com.krizaka.sovkora.conversation.application.service.CheckRateLimitService;
import com.krizaka.sovkora.conversation.application.service.DocumentReadService;
import com.krizaka.sovkora.conversation.domain.port.DocumentReadLedger;
import com.krizaka.sovkora.conversation.domain.port.DocumentReader;
import com.krizaka.sovkora.conversation.domain.port.ReaderRoutingStore;
import com.krizaka.sovkora.conversation.infrastructure.config.DocumentReaderProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the service behind {@code /api/v1/document-reads} (krizaka-internal#4198), beside {@link
 * ChecksDoorConfiguration}: the root package, so wiring the application service adds no edge from
 * infrastructure to application.
 */
@Configuration(proxyBeanMethods = false)
public class DocumentReaderConfiguration {

  /**
   * @param reader the document readers
   * @param routing the routing rows
   * @param ledger the measurement per page
   * @param properties the door's settings
   * @return the service behind the door
   */
  @Bean
  public DocumentReadService documentReadService(
      DocumentReader reader,
      ReaderRoutingStore routing,
      DocumentReadLedger ledger,
      DocumentReaderProperties properties) {
    return new DocumentReadService(
        reader,
        routing,
        ledger,
        new CheckRateLimitService(properties.ratePerMinute(), Clock.systemUTC()),
        properties.requiredRoles());
  }
}
