package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import java.util.List;

/** The document reads a product asks for over HTTP (krizaka-internal#4198). */
public interface DocumentReads {

  /**
   * @param actor the verified caller
   * @return whether the caller holds one of the allowed roles
   */
  boolean permits(Actor actor);

  /**
   * @param actor the verified caller
   * @return {@code 0} when the call may go on, else the seconds to wait
   */
  long throttle(Actor actor);

  /**
   * Reads the pages by the routing as it stands now and measures each page read.
   *
   * @param actor the verified caller, already permitted
   * @param pages the pages, in order
   * @return one reading per page, or the refusal
   */
  ReadResult read(Actor actor, List<DocumentPage> pages);
}
