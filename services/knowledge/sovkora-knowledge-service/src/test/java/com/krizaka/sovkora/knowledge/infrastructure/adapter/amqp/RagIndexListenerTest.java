package com.krizaka.sovkora.knowledge.infrastructure.adapter.amqp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.sovkora.knowledge.application.service.IngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagIndexListenerTest {

  private final IngestionService ingestionService = mock(IngestionService.class);
  private final MessageDedup dedup = mock(MessageDedup.class);
  private final RagIndexListener listener = new RagIndexListener(ingestionService, dedup);

  @Test
  @DisplayName("A fresh index command is ingested and then marked processed")
  void freshCommandIsIngested() {
    when(dedup.claim("knowledge.rag-index", "m-1")).thenReturn(true);

    listener.onIndexCommand(new RagIndexCommand("searchWeb"), "m-1");

    verify(ingestionService).ingest("searchWeb");
    verify(dedup).claim("knowledge.rag-index", "m-1");
  }

  @Test
  @DisplayName("A duplicate index command is skipped")
  void duplicateIsSkipped() {
    when(dedup.claim("knowledge.rag-index", "m-1")).thenReturn(false);

    listener.onIndexCommand(new RagIndexCommand("searchWeb"), "m-1");

    verify(ingestionService, never()).ingest("searchWeb");
  }
}
