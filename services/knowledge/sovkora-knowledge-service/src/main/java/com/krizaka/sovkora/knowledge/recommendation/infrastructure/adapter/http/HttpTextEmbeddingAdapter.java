package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.http;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.EmbeddingUnavailableException;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Vector;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.TextEmbeddingClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The platform's embedding model over HTTP ({@code POST /api/v1/embeddings} of the conversation
 * service — the same endpoint a product calls), <em>on behalf of the caller</em>: the request
 * carries the tenant's own session JWT, so the embedding run is the tenant's in Sovkora's logs and
 * its quota, and no second credential exists. The model is the catalogue's {@code embedding}
 * default — never named here.
 */
@Component
class HttpTextEmbeddingAdapter implements TextEmbeddingClient {

  private final RestClient client;

  HttpTextEmbeddingAdapter(RestClient recommendationEmbeddingRestClient) {
    this.client = Objects.requireNonNull(recommendationEmbeddingRestClient, "client");
  }

  @Override
  public Embeddings embed(List<String> texts) {
    EmbeddingAnswer answer;
    try {
      answer =
          client
              .post()
              .uri("/api/v1/embeddings")
              .header("Authorization", "Bearer " + callerToken())
              .body(Map.of("input", texts))
              .retrieve()
              .body(EmbeddingAnswer.class);
    } catch (RestClientException e) {
      throw new EmbeddingUnavailableException("the embedding model did not answer", e);
    }
    if (answer == null || answer.data() == null || answer.data().size() != texts.size()) {
      throw new EmbeddingUnavailableException("the embedding model answered another shape");
    }
    List<Vector> vectors = new ArrayList<>(texts.size());
    List<EmbeddingAnswer.Datum> ordered =
        answer.data().stream()
            .sorted(Comparator.comparingInt(EmbeddingAnswer.Datum::index))
            .toList();
    for (EmbeddingAnswer.Datum datum : ordered) {
      try {
        vectors.add(Vector.of(datum.embedding()));
      } catch (IllegalArgumentException e) {
        throw new EmbeddingUnavailableException(
            "the embedding model's vectors do not fit the recommender: " + e.getMessage(), e);
      }
    }
    return new Embeddings(answer.runId(), answer.model(), vectors);
  }

  private static String callerToken() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt) {
      return jwt.getToken().getTokenValue();
    }
    throw new EmbeddingUnavailableException("no caller session to embed on behalf of");
  }

  /** The embeddings endpoint's answer (only what the recommender reads). */
  record EmbeddingAnswer(String runId, String model, List<Datum> data) {

    record Datum(int index, float[] embedding) {

      @Override
      public boolean equals(Object other) {
        return other instanceof Datum(var idx, var emb)
            && index == idx
            && java.util.Arrays.equals(embedding, emb);
      }

      @Override
      public int hashCode() {
        return 31 * Integer.hashCode(index) + java.util.Arrays.hashCode(embedding);
      }

      @Override
      public String toString() {
        return "Datum[index=" + index + ", embedding=" + java.util.Arrays.toString(embedding) + "]";
      }
    }
  }
}
