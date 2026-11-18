package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadRefusal;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.port.DocumentReads;
import com.krizaka.sovkora.conversation.infrastructure.adapter.rest.dto.DocumentReadsRequest;
import com.krizaka.sovkora.conversation.infrastructure.adapter.rest.dto.DocumentReadsResponse;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import com.krizaka.users.domain.model.User;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one door for products to read document pages through Sovkora (krizaka-internal#4198): pages
 * in, one reading per page out, each with the reader that read it and what it cost and took. The
 * caller is the verified actor of the session, with a role and within a rate limit, as at the
 * checks' door (ADR-091); the reader is the routing's ({@code document_reader_setting}), never the
 * caller's. A refusal is a {@code ProblemDetail} with a {@code code}, never a reader's message.
 */
@RestController
@RequestMapping("/api/v1/document-reads")
public class DocumentReadController {

  /** The access audit of ADR-091: who was refused, never what was sent. */
  private static final Logger ACCESS_AUDIT = LoggerFactory.getLogger(DocumentReadController.class);

  private static final Marker ACCESS = MarkerFactory.getMarker("ACCESS_AUDIT");

  private final DocumentReads reads;

  /**
   * @param reads the read service
   */
  public DocumentReadController(DocumentReads reads) {
    this.reads = reads;
  }

  /**
   * Reads document pages: one reading per page, in order.
   *
   * @param request the pages
   * @param user the authenticated caller
   * @return the readings; 401 without a verified actor, 403 without the role, 429 over the rate
   *     limit, 400 for a malformed page, 413 over the page budget, 503 or 504 when no reader could
   *     answer
   */
  @PostMapping
  public ResponseEntity<?> read(
      @RequestBody DocumentReadsRequest request, @AuthenticationPrincipal @Nullable User user) {
    Actor actor = actorOf(user);
    if (actor == null) {
      return ResponseEntity.status(401).build();
    }
    if (!reads.permits(actor)) {
      refused(actor, "MISSING_ROLE");
      return ResponseEntity.status(403).build();
    }
    long wait = reads.throttle(actor);
    if (wait > 0) {
      refused(actor, "RATE_LIMIT");
      return ResponseEntity.status(429).header("Retry-After", Long.toString(wait)).build();
    }
    Optional<List<DocumentPage>> pages = request.toPages();
    if (pages.isEmpty()) {
      return problem(ReadRefusal.INPUT_INVALID);
    }
    ReadResult result = reads.read(actor, pages.get());
    return switch (result) {
      case ReadResult.Readings(var readings) ->
          ResponseEntity.ok(DocumentReadsResponse.of(readings));
      case ReadResult.Refused(var refusal) -> problem(refusal);
    };
  }

  private static ResponseEntity<ProblemDetail> problem(ReadRefusal refusal) {
    ProblemDetail problem = ProblemDetail.forStatus(refusal.status());
    problem.setProperty("code", refusal.name());
    return ResponseEntity.status(refusal.status()).body(problem);
  }

  private static void refused(Actor actor, String reason) {
    ACCESS_AUDIT.warn(
        ACCESS,
        "document reads refused tenant={} subject={} reason={}",
        actor.tenant(),
        actor.subject(),
        reason);
  }

  /** The actor of the session: none for a caller that is not a signed-in, unambiguous user. */
  private static @Nullable Actor actorOf(@Nullable User user) {
    return user == null ? null : Actor.of(user.id().toString(), user.authorities()).orElse(null);
  }
}
