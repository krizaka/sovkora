package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The reader worker's one endpoint, declared; {@code HttpDocumentReaderAdapter} adapts it. */
@HttpExchange("/v1")
interface ReaderExchange {

  @PostExchange("/document-reads")
  ReaderWire.Answer read(@RequestBody ReaderWire.Ask body);
}
