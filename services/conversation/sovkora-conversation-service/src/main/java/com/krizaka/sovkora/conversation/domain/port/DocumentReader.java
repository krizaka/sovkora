package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;
import java.util.List;

/** The document readers (workers/reader), reached by the door only (krizaka-internal#4198). */
public interface DocumentReader {

  /**
   * Reads pages by the routing of this call.
   *
   * @param pages the pages, in order
   * @param routing the routing read from the database for this call
   * @return the readings, or the refusal; never an exception for a worker that is down
   */
  ReadResult read(List<DocumentPage> pages, ReaderRouting routing);
}
