package com.krizaka.sovkora.conversation.application.service;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.port.DocumentReadLedger;
import com.krizaka.sovkora.conversation.domain.port.DocumentReader;
import com.krizaka.sovkora.conversation.domain.port.DocumentReads;
import com.krizaka.sovkora.conversation.domain.port.ReaderRoutingStore;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import java.util.List;
import java.util.Set;

/**
 * The pages a product asks Sovkora to read (krizaka-internal#4198), through one door. The caller is
 * a verified actor holding one of the allowed roles and within its rate limit, as at the checks'
 * door (ADR-091); the reader is the routing's, read from the database at each call and never taken
 * from the caller; every page read is measured under the caller's tenant before it is answered.
 */
public class DocumentReadService implements DocumentReads {

  private final DocumentReader reader;
  private final ReaderRoutingStore routing;
  private final DocumentReadLedger ledger;
  private final CheckRateLimitService limiter;
  private final Set<String> allowedRoles;

  /**
   * @param reader the document readers
   * @param routing where the routing lives
   * @param ledger where each page read is measured
   * @param limiter the per-actor rate limit
   * @param allowedRoles the roles any of which may use the door
   */
  public DocumentReadService(
      DocumentReader reader,
      ReaderRoutingStore routing,
      DocumentReadLedger ledger,
      CheckRateLimitService limiter,
      Set<String> allowedRoles) {
    this.reader = reader;
    this.routing = routing;
    this.ledger = ledger;
    this.limiter = limiter;
    this.allowedRoles = Set.copyOf(allowedRoles);
  }

  @Override
  public boolean permits(Actor actor) {
    return actor.holdsAny(allowedRoles);
  }

  @Override
  public long throttle(Actor actor) {
    return limiter.tryAcquire(actor.tenant() + "/" + actor.subject());
  }

  @Override
  public ReadResult read(Actor actor, List<DocumentPage> pages) {
    ReadResult result = reader.read(pages, routing.current());
    if (result instanceof ReadResult.Readings read) {
      for (int i = 0; i < pages.size(); i++) {
        ledger.record(actor, pages.get(i).kind(), read.readings().get(i));
      }
    }
    return result;
  }
}
