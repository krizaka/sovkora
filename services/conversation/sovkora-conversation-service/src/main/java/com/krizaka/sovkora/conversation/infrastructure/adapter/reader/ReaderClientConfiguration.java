package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import com.krizaka.sovkora.conversation.domain.port.DocumentReader;
import com.krizaka.sovkora.conversation.infrastructure.config.DocumentReaderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires {@link DocumentReader} to the reader worker (krizaka-internal#4198). The only public type
 * of this package: the exchange, its wire records, the adapter and the group configurer stay
 * package-private.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ReaderHttpGroupConfigurer.GROUP, types = ReaderExchange.class)
public class ReaderClientConfiguration {

  /**
   * @param properties the door's settings
   * @return the group's timeout and service token
   */
  @Bean
  RestClientHttpServiceGroupConfigurer readerHttpGroupConfigurer(
      DocumentReaderProperties properties) {
    return new ReaderHttpGroupConfigurer(properties.serviceToken(), properties.readTimeout());
  }

  /**
   * @param exchange the declared endpoint
   * @return the readers behind the door
   */
  @Bean
  DocumentReader httpDocumentReader(ReaderExchange exchange) {
    return new HttpDocumentReaderAdapter(exchange);
  }
}
