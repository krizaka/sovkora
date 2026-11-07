package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON a model returns for a grounded call: {@code {"claims": [{"text", "citations": [{"url",
 * "quote", "contentHash", "fetchedAt"}]}]}}. It is read as a proposal: every status is reset to
 * {@code TO_VALIDATE}, so nothing the model says about its own claims is believed; the verifier
 * decides.
 */
public final class GroundedAnswerJson {

  private static final JsonMapper JSON =
      JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .changeDefaultPropertyInclusion(
              v -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null))
          .build();

  /** One citation as the model wrote it. */
  record CitationJson(String url, String quote, String contentHash, String fetchedAt) {}

  /** One claim as the model wrote it. */
  record ClaimJson(String text, List<CitationJson> citations) {}

  /** The model's answer. */
  record AnswerJson(List<ClaimJson> claims) {}

  private GroundedAnswerJson() {}

  /**
   * Reads a model's answer, tolerating a Markdown code fence around the JSON.
   *
   * @param content the model's content
   * @return the claims as proposals, all {@code TO_VALIDATE}; no prose is kept
   * @throws IllegalArgumentException when the content is not JSON of that shape
   */
  public static GroundedAnswer read(String content) {
    try {
      AnswerJson parsed = JSON.readValue(unfence(content), AnswerJson.class);
      List<ClaimJson> claims =
          parsed == null || parsed.claims() == null ? List.of() : parsed.claims();
      return new GroundedAnswer(
          "",
          claims.stream()
              .filter(c -> c != null && c.text() != null && !c.text().isBlank())
              .map(GroundedAnswerJson::toClaim)
              .toList(),
          List.of());
    } catch (JacksonException e) {
      throw new IllegalArgumentException("the model's answer is not the citation shape", e);
    }
  }

  private static Claim toClaim(ClaimJson claim) {
    List<Citation> citations =
        claim.citations() == null
            ? List.of()
            : claim.citations().stream()
                .filter(c -> c != null && c.url() != null && c.quote() != null)
                .map(c -> new Citation(c.url(), c.quote(), c.contentHash(), c.fetchedAt()))
                .toList();
    return new Claim(claim.text(), ClaimStatus.TO_VALIDATE, null, citations);
  }

  private static String unfence(String content) {
    String text = content == null ? "" : content.strip();
    if (text.startsWith("```")) {
      int firstLine = text.indexOf('\n');
      int end = text.lastIndexOf("```");
      if (firstLine > 0 && end > firstLine) {
        return text.substring(firstLine + 1, end).strip();
      }
    }
    return text;
  }
}
