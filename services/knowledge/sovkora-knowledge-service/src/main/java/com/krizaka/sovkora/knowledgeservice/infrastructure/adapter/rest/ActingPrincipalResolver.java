package com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads who a call is made for from the {@code act} claim of its verified service token (ADR-091).
 * The token's signature was checked by the resource server before a controller runs, so the claim
 * is the core's word and not the caller's.
 */
final class ActingPrincipalResolver {

  /** The claim that carries the principal. */
  static final String CLAIM = "act";

  private ActingPrincipalResolver() {}

  static Optional<ActingPrincipal> resolve(Jwt token) {
    return token == null ? Optional.empty() : ActingPrincipal.fromClaim(token.getClaim(CLAIM));
  }
}
