package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest;

import com.krizaka.sovkora.knowledgeservice.recommendation.application.service.CatalogueService;
import com.krizaka.sovkora.knowledgeservice.recommendation.application.service.CreatorInsightService;
import com.krizaka.sovkora.knowledgeservice.recommendation.application.service.FeedService;
import com.krizaka.sovkora.knowledgeservice.recommendation.application.service.InteractionService;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.CatalogueResult;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.CreatorInsights;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.EmbeddingUnavailableException;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.ErasureResult;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Feed;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Identifiers;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.IngestResult;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationsDisabledException;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.UnknownItemException;
import com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto.ErasureRequest;
import com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto.EventBatchRequest;
import com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto.FeedResponse;
import com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto.ItemBatchRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The recommendations resource (ADR-078 §6), reached through the edge with a product's service key
 * (scopes {@code reco:sync} and {@code reco:read}). The tenant is the key's account — the JWT
 * subject — and every call stays inside it.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

  /** Header carrying the request id, so a client that ignores the body can still log it. */
  static final String REQUEST_ID_HEADER = "X-Sovkora-Request-Id";

  private static final int MAX_LIMIT = 50;
  private static final int MAX_EXCLUDE = 200;

  private final InteractionService interactions;
  private final CatalogueService catalogue;
  private final FeedService feeds;
  private final CreatorInsightService insights;

  public RecommendationController(
      InteractionService interactions,
      CatalogueService catalogue,
      FeedService feeds,
      CreatorInsightService insights) {
    this.interactions = Objects.requireNonNull(interactions, "interactions");
    this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
    this.feeds = Objects.requireNonNull(feeds, "feeds");
    this.insights = Objects.requireNonNull(insights, "insights");
  }

  /** Records a batch of interaction events (idempotent by id). */
  @PostMapping("/events")
  public IngestResult events(
      @AuthenticationPrincipal Jwt jwt, @RequestBody EventBatchRequest body) {
    return interactions.record(tenant(jwt), body.toInteractions());
  }

  /** Upserts eligible items; the ones whose text changed are embedded. */
  @PostMapping("/items")
  public CatalogueResult items(
      @AuthenticationPrincipal Jwt jwt, @RequestBody ItemBatchRequest body) {
    return catalogue.upsert(tenant(jwt), body.toItems());
  }

  /** Forgets people and items. */
  @PostMapping("/erasures")
  public ErasureResult erasures(
      @AuthenticationPrincipal Jwt jwt, @RequestBody ErasureRequest body) {
    return catalogue.erase(tenant(jwt), body.users(), body.items());
  }

  /** The person's "For you" feed. */
  @GetMapping("/for-you")
  public ResponseEntity<FeedResponse> forYou(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("user") String user,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "exclude", required = false) String exclude) {
    Feed feed =
        feeds.forYou(tenant(jwt), Identifiers.requirePseudonym(user), limit(limit), ids(exclude));
    return served(feed);
  }

  /** Items like an item. */
  @GetMapping("/similar")
  public ResponseEntity<FeedResponse> similar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("item") String item,
      @RequestParam(value = "limit", required = false) Integer limit,
      @RequestParam(value = "exclude", required = false) String exclude) {
    Feed feed =
        feeds.similar(tenant(jwt), Identifiers.require(item, "item"), limit(limit), ids(exclude));
    return served(feed);
  }

  /** A creator's analytics. */
  @GetMapping("/insights")
  public CreatorInsights insights(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("creator") String creator) {
    return insights.insights(tenant(jwt), Identifiers.require(creator, "creator"));
  }

  private static ResponseEntity<FeedResponse> served(Feed feed) {
    return ResponseEntity.ok()
        .header(REQUEST_ID_HEADER, feed.requestId().toString())
        .body(FeedResponse.of(feed));
  }

  private static String tenant(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw new IllegalStateException("no tenant: the request carries no session");
    }
    return jwt.getSubject();
  }

  private static int limit(Integer limit) {
    if (limit == null) {
      return 20;
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
    return limit;
  }

  private static Set<String> ids(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(s -> ids.add(Identifiers.require(s, "exclude")));
    if (ids.size() > MAX_EXCLUDE) {
      throw new IllegalArgumentException("exclude holds at most " + MAX_EXCLUDE + " ids");
    }
    return ids;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(UnknownItemException.class)
  ResponseEntity<ProblemDetail> unknown(UnknownItemException e) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({RecommendationsDisabledException.class, EmbeddingUnavailableException.class})
  ResponseEntity<ProblemDetail> unavailable(RuntimeException e) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
