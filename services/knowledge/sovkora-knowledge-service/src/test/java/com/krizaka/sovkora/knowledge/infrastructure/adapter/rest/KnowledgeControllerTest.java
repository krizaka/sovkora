package com.krizaka.sovkora.knowledge.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.knowledge.application.service.KnowledgeQueryService;
import com.krizaka.sovkora.knowledge.application.service.SourceAclService;
import com.krizaka.sovkora.knowledge.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledge.domain.port.AccessAudit;
import com.krizaka.sovkora.knowledge.domain.port.AccessAudit.Reason;
import com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto.ContentResponse;
import com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto.RetrieveRequest;
import com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto.SourceAclRequest;
import com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto.SourceSearchRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

class KnowledgeControllerTest {

  private final KnowledgeQueryService queryService = mock(KnowledgeQueryService.class);
  private final SourceAclService aclService = mock(SourceAclService.class);
  private final AccessAudit audit = mock(AccessAudit.class);
  private final KnowledgeController controller =
      new KnowledgeController(queryService, aclService, audit);

  private static Jwt acting(Map<String, Object> principal) {
    Jwt.Builder token =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject("sovkora-engine-core")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    if (principal != null) {
      token.claim("act", principal);
    }
    return token.build();
  }

  private static final Map<String, Object> EMPLOYEE =
      Map.of("sub", "emp-1", "tenant", "acme", "roles", List.of("ROLE_USER"), "groups", List.of());

  @Test
  @DisplayName("retrieve answers for the principal of the token (defaulting topK)")
  void retrieveReturnsContext() {
    when(queryService.retrieveContext(any(ActingPrincipal.class), eq("q"), eq(3)))
        .thenReturn("ctx");

    ResponseEntity<ContentResponse> response =
        controller.retrieve(acting(EMPLOYEE), new RetrieveRequest("q", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().content()).isEqualTo("ctx");
  }

  @Test
  @DisplayName("sources/search answers for the principal of the token")
  void searchReturnsSources() {
    when(queryService.searchSources(any(ActingPrincipal.class), eq("searchKnowledge"), eq("q")))
        .thenReturn("joined");

    ResponseEntity<ContentResponse> response =
        controller.searchSources(acting(EMPLOYEE), new SourceSearchRequest("searchKnowledge", "q"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().content()).isEqualTo("joined");
  }

  @Test
  @DisplayName("a token that acts for nobody, or for a malformed principal, is refused and audited")
  void noPrincipalIsRefused() {
    for (Jwt token :
        List.of(
            acting(null),
            acting(Map.of("sub", "emp-1")),
            acting(Map.of("sub", "emp-1", "tenant", "*")),
            acting(Map.of("sub", "emp\" or 1=1", "tenant", "acme")))) {
      assertThat(controller.retrieve(token, new RetrieveRequest("q", 3)).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              controller
                  .searchSources(token, new SourceSearchRequest("searchKnowledge", "q"))
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    verifyNoInteractions(queryService);
    verify(audit, org.mockito.Mockito.times(8))
        .denied(eq(Optional.empty()), any(), eq("request"), eq("n/a"), eq(Reason.NO_PRINCIPAL));
  }

  @Test
  @DisplayName("changing an ACL maps the outcome to the status")
  void aclUpdateStatuses() {
    when(aclService.replace(any(ActingPrincipal.class), eq(7L), any()))
        .thenReturn(SourceAclService.Outcome.CHANGED);
    when(aclService.replace(any(ActingPrincipal.class), eq(8L), any()))
        .thenReturn(SourceAclService.Outcome.FORBIDDEN);

    SourceAclRequest body = new SourceAclRequest(List.of("role:ROLE_HR"));
    assertThat(controller.replaceAcl(acting(EMPLOYEE), 7L, body).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.replaceAcl(acting(EMPLOYEE), 8L, body).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(controller.replaceAcl(acting(null), 7L, body).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
