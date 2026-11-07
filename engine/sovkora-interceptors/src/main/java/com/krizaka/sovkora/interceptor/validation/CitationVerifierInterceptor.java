package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.pipeline.ScreenedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.research.*;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The verifier stage of grounded research (ADR-087 §3): an answer in the citation shape is checked
 * claim by claim against the documents research read <b>for this turn</b> — the ones the grounding
 * stage recorded in the context ({@link PromptContext#researchRead()}) — by the {@link
 * CitationVerifier}, before it is served; the served answer is the one the verifier renders from
 * the claims, never the model's prose. Any other answer passes through untouched — this interceptor
 * only reads what it can verify. The mode is {@code research.verify.mode} ({@code MARK} by
 * default), read at each answer.
 *
 * <p>Its {@code pipeline_interceptor_config} row ships disabled; the {@code grounding} flag of
 * slice 5 turns it on per request.
 */
public class CitationVerifierInterceptor implements PromptContextInterceptor {

  /** The machine-readable refusal code of a streamed answer with unverified claims. */
  public static final String REASON_UNVERIFIED = "citations_unverified";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CitationVerifier verifier;
  private final RuntimeConfigProvider config;
  private final PipelineConfigProvider pipelineRows;

  /**
   * Wires the stage.
   *
   * @param verifier the deterministic verifier
   * @param config the settings
   * @param pipelineRows the pipeline rows, where this screen's own switch lives
   */
  public CitationVerifierInterceptor(
      CitationVerifier verifier,
      RuntimeConfigProvider config,
      PipelineConfigProvider pipelineRows) {
    this.pipelineRows =
        Objects.requireNonNull(pipelineRows, "PipelineConfigProvider cannot be null");
    this.verifier = Objects.requireNonNull(verifier, "CitationVerifier cannot be null");
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  /**
   * A verification that fails must stop the turn: serving the model's own prose and its own {@code
   * VERIFIED} statuses unchecked is exactly what this screen exists to prevent.
   */
  @Override
  public boolean failsClosed() {
    return true;
  }

  /**
   * The screen is reached by every turn ({@code Engine} hands it all the interceptor beans,
   * whatever chain the pipeline selected), so it checks its own switches: research on, and its own
   * pipeline row present and enabled. Off means no rewrite.
   */
  private boolean active() {
    if (!config.getBoolean("research.enabled", false)) {
      return false;
    }
    return pipelineRows.findAllOrdered().stream()
        .anyMatch(row -> getId().equals(row.interceptorKey()) && row.enabled());
  }

  @Override
  public String screenResponse(PromptContext context, String responseText) {
    GroundedAnswer verified = verify(context, responseText);
    return verified == null ? responseText : write(verified);
  }

  /**
   * A streamed answer has already left in chunks: it cannot be rewritten the way a served one is. A
   * citation-shaped answer whose claims do not all verify is therefore refused, whatever the mode
   * (sovkora-engine#127); one that verifies is accepted as it was streamed. The check needs the
   * whole JSON, so nothing is read per chunk.
   */
  @Override
  public ScreenedResponse screenStreamed(PromptContext context, String assembled) {
    GroundedAnswer verified = verify(context, assembled);
    if (verified != null
        && verified.claims().stream().anyMatch(c -> c.status() != ClaimStatus.VERIFIED)) {
      throw new PipelineShortCircuitException(
          getId(),
          REASON_UNVERIFIED,
          "The answer cites claims that could not be verified and was not served.",
          null);
    }
    return ScreenedResponse.plain(assembled);
  }

  /**
   * The answer checked against this turn's reads, or {@code null} when there is nothing to check.
   */
  private GroundedAnswer verify(PromptContext context, String responseText) {
    if (!active()) {
      return null;
    }
    JsonNode root = parse(responseText);
    if (root == null
        || !root.isObject()
        || !root.path("claims").isArray()
        || !root.path("answer").isString()) {
      return null;
    }
    VerifyMode mode =
        "STRICT".equalsIgnoreCase(config.getString("research.verify.mode", "MARK"))
            ? VerifyMode.STRICT
            : VerifyMode.MARK;
    return verifier.verify(read(root), mode, context.researchRead());
  }

  private static JsonNode parse(String text) {
    if (text == null || text.isBlank() || text.strip().charAt(0) != '{') {
      return null;
    }
    try {
      return MAPPER.readTree(text);
    } catch (JacksonException notJson) {
      return null;
    }
  }

  static GroundedAnswer read(JsonNode root) {
    List<Claim> claims = new ArrayList<>();
    for (JsonNode c : root.path("claims")) {
      List<Citation> citations = new ArrayList<>();
      for (JsonNode cite : c.path("citations")) {
        citations.add(
            new Citation(
                cite.path("url").asString(""),
                cite.path("quote").asString(""),
                cite.path("contentHash").asString(""),
                cite.path("fetchedAt").asString("")));
      }
      // The model's own status is not trusted: the verifier sets it.
      claims.add(new Claim(c.path("text").asString(""), ClaimStatus.TO_VALIDATE, null, citations));
    }
    List<GroundedSource> sources = new ArrayList<>();
    for (JsonNode s : root.path("sources")) {
      sources.add(
          new GroundedSource(
              s.path("url").asString(""),
              s.path("title").asString(""),
              s.path("sourceKey").asString("")));
    }
    return new GroundedAnswer(root.path("answer").asString(""), claims, sources);
  }

  static String write(GroundedAnswer answer) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("answer", answer.answer());
    ArrayNode claims = root.putArray("claims");
    for (Claim claim : answer.claims()) {
      ObjectNode c = claims.addObject();
      c.put("text", claim.text());
      c.put("status", claim.status().name());
      if (claim.reason() != null) {
        c.put("reason", claim.reason().name());
      }
      ArrayNode citations = c.putArray("citations");
      for (Citation cite : claim.citations()) {
        ObjectNode n = citations.addObject();
        n.put("url", cite.url());
        n.put("quote", cite.quote());
        n.put("contentHash", cite.contentHash());
        n.put("fetchedAt", cite.fetchedAt());
      }
    }
    ArrayNode sources = root.putArray("sources");
    for (GroundedSource s : answer.sources()) {
      sources
          .addObject()
          .put("url", s.url())
          .put("title", s.title())
          .put("sourceKey", s.sourceKey());
    }
    return root.toString();
  }
}
