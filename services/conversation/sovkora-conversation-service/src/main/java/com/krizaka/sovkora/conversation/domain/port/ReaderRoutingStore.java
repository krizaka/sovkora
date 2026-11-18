package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;

/** Where the document readers' routing lives: database rows, read at each call. */
public interface ReaderRoutingStore {

  /**
   * @return the routing as it stands now
   */
  ReaderRouting current();
}
