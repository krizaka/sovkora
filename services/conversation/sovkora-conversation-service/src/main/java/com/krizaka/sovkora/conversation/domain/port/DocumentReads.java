package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import java.util.List;

/**
 * The document reads a product asks for over HTTP (krizaka-internal#4198), behind the gate every
 * product door shares.
 */
public interface DocumentReads extends ProductDoor {

  /**
   * Reads the pages by the routing as it stands now and measures each page read.
   *
   * @param actor the verified caller, already permitted
   * @param pages the pages, in order
   * @return one reading per page, or the refusal
   */
  ReadResult read(Actor actor, List<DocumentPage> pages);
}
