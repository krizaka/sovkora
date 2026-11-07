package com.krizaka.sovkora.adapter.springai.remote;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP implementation of the {@link KnowledgeService} port against the knowledge service's internal
 * API (Phase 4 cutover). Every failure degrades to an empty string: RAG enrichment and tool source
 * lookup are additive concerns, so an unreachable knowledge service must never fail the calling
 * request.
 */
public class HttpKnowledgeAdapter implements KnowledgeService {

  private static final Logger logger = LoggerFactory.getLogger(HttpKnowledgeAdapter.class);

  private final RestClient knowledgeClient;
  private final ActingServiceToken tokens;

  public HttpKnowledgeAdapter(KnowledgeProperties properties) {
    this.tokens = new ActingServiceToken(properties.serviceSecret(), "sovkora-core");
    this.knowledgeClient = RestClient.builder().baseUrl(properties.baseUrl().toString()).build();
  }

  /** Wire shape of the internal knowledge responses (contract copy — no shared jar). */
  record ContentResponse(String content) {}

  @Override
  public String retrieveContext(Actor actor, String query, int topK) {
    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put("topK", topK);
    return post(actor, "/internal/v1/knowledge/retrieve", request);
  }

  @Override
  public String searchSources(Actor actor, String toolId, String query) {
    Map<String, Object> request = new HashMap<>();
    request.put("toolId", toolId);
    request.put("query", query);
    return post(actor, "/internal/v1/knowledge/sources/search", request);
  }

  private String post(Actor actor, String path, Map<String, Object> request) {
    if (actor == null) {
      // Acting for nobody reads nothing; the knowledge service would refuse it anyway.
      return "";
    }
    try {
      ContentResponse response =
          knowledgeClient
              .post()
              .uri(path)
              // The token is minted per call and names this actor in its signed act claim, so the
              // knowledge service filters for them and for nobody else (ADR-091).
              .headers(headers -> headers.setBearerAuth(tokens.tokenFor(actor)))
              .header("Content-Type", "application/json")
              .body(request)
              .retrieve()
              .body(ContentResponse.class);
      return response != null && response.content() != null ? response.content() : "";
    } catch (Exception e) {
      logger.warn(
          "Knowledge service call {} failed — degrading to empty content: {}",
          path,
          e.getMessage());
      return "";
    }
  }
}
