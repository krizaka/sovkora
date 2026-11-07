package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import java.util.List;

/**
 * The model port of a grounded call (ADR-087 §1): given a question and the documents read, the
 * model answers in the citation shape. It is the only place a retrieval calls a model, and a cached
 * retrieval never reaches it.
 *
 * <p>The documents reach the model as data, never as instructions (ADR-087 §4); the answer it
 * returns is a proposal that the verifier checks, never a fact.
 */
public interface GroundedClaimsModel {

  /** A document handed to the model: where it was read, its hash, its title and its text. */
  record Passage(String url, String contentHash, String title, String text) {}

  /**
   * Asks the grounded question.
   *
   * @param question the question
   * @param language {@code fr} or {@code en}, the language to answer in
   * @param passages the documents read for this question
   * @return the answer, its claims citing the passages by URL, quote and hash
   */
  GroundedAnswer ask(String question, String language, List<Passage> passages);

  /** An answer and the tokens its call consumed, as the provider reported them. */
  record Reply(GroundedAnswer answer, TokenUsage usage) {}

  /**
   * Asks the grounded question and reports what the call consumed, for the caller to bill. A model
   * that cannot tell reports {@link TokenUsage#none()}: unreported, never zero.
   *
   * @param question the question
   * @param language {@code fr} or {@code en}
   * @param passages the documents read for this question
   * @return the answer and the tokens of the call
   */
  default Reply askMetered(String question, String language, List<Passage> passages) {
    return new Reply(ask(question, language, passages), TokenUsage.none());
  }
}
