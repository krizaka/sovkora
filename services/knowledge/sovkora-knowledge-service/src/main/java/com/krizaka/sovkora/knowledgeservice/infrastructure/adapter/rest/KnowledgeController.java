package com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.knowledgeservice.application.service.KnowledgeQueryService;
import com.krizaka.sovkora.knowledgeservice.application.service.SourceAclService;
import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledgeservice.domain.port.AccessAudit;
import com.krizaka.sovkora.knowledgeservice.domain.port.AccessAudit.Reason;
import com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto.ContentResponse;
import com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto.RetrieveRequest;
import com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto.SourceAclRequest;
import com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto.SourceSearchRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal knowledge read surface. Consumed by the core's {@code KnowledgeService} HTTP adapter;
 * internal-only (the {@code SERVICE} authority — never reachable through the edge). Both read
 * endpoints return a uniform {@link ContentResponse} whose content is empty when nothing is
 * available.
 *
 * <p><b>Every call is made for a principal</b> (ADR-091): the {@code act} claim of the verified
 * token. A call that acts for nobody is refused with 403 and audited, whatever its body says — the
 * body names no user, tenant or role.
 */
@RestController
@RequestMapping("/internal/v1/knowledge")
public class KnowledgeController {

  private final KnowledgeQueryService knowledgeQueryService;
  private final SourceAclService sourceAclService;
  private final AccessAudit audit;

  public KnowledgeController(
      KnowledgeQueryService knowledgeQueryService,
      SourceAclService sourceAclService,
      AccessAudit audit) {
    this.knowledgeQueryService =
        Objects.requireNonNull(knowledgeQueryService, "KnowledgeQueryService required");
    this.sourceAclService = Objects.requireNonNull(sourceAclService, "SourceAclService required");
    this.audit = Objects.requireNonNull(audit, "AccessAudit required");
  }

  /** Semantic RAG retrieval over the vector store, among the passages the principal may read. */
  @PostMapping("/retrieve")
  public ResponseEntity<ContentResponse> retrieve(
      @AuthenticationPrincipal Jwt token, @RequestBody RetrieveRequest request) {
    return forPrincipal(
        token,
        "retrieve",
        principal ->
            ResponseEntity.ok(
                new ContentResponse(
                    knowledgeQueryService.retrieveContext(
                        principal, request.query(), request.resolvedTopK()))));
  }

  /** Substring search over a tool's registered RAG sources the principal may read. */
  @PostMapping("/sources/search")
  public ResponseEntity<ContentResponse> searchSources(
      @AuthenticationPrincipal Jwt token, @RequestBody SourceSearchRequest request) {
    return forPrincipal(
        token,
        "search",
        principal ->
            ResponseEntity.ok(
                new ContentResponse(
                    knowledgeQueryService.searchSources(
                        principal, request.toolId(), request.query()))));
  }

  /** Replaces who may read a source; an administrator of the source's tenant only. */
  @PutMapping("/sources/{id}/acl")
  public ResponseEntity<Void> replaceAcl(
      @AuthenticationPrincipal Jwt token,
      @PathVariable long id,
      @RequestBody SourceAclRequest request) {
    return forPrincipal(
        token,
        "acl-update",
        admin ->
            switch (sourceAclService.replace(admin, id, request.principals())) {
              case CHANGED -> ResponseEntity.noContent().<Void>build();
              case NOT_FOUND -> ResponseEntity.notFound().build();
              case FORBIDDEN -> ResponseEntity.status(403).build();
              case INVALID -> ResponseEntity.badRequest().build();
            });
  }

  private <T> ResponseEntity<T> forPrincipal(
      Jwt token, String action, Function<ActingPrincipal, ResponseEntity<T>> handler) {
    Optional<ActingPrincipal> principal = ActingPrincipalResolver.resolve(token);
    if (principal.isEmpty()) {
      audit.denied(Optional.empty(), action, "request", "n/a", Reason.NO_PRINCIPAL);
      return ResponseEntity.status(403).build();
    }
    return handler.apply(principal.get());
  }
}
