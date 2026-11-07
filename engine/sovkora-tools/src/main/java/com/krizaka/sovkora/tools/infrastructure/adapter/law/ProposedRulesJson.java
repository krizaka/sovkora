package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.krizaka.sovkora.tools.domain.model.law.LawBlock;
import com.krizaka.sovkora.tools.domain.model.law.ProposedRule;
import com.krizaka.sovkora.tools.domain.model.law.VetReport;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON of stage 3: a model answer in the contract of the {@code propose} step ({@code {"rules":
 * [ … ]}}, each rule in the shape of a ruleset rule, sovkora-packs {@code
 * document-validation/derivation/proposed-ruleset.schema.json}), and the JSONB columns of {@code
 * rule_proposal}. A fixture side may be one fragment or a list of them, as in a ruleset. Absent
 * parts are left out when written, so a promoted rule is the ruleset's own shape.
 */
public final class ProposedRulesJson {

  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .changeDefaultPropertyInclusion(
              v -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null))
          .build();

  /** The answer of the {@code propose} step. */
  record Answer(List<ProposedRule> rules) {}

  private ProposedRulesJson() {}

  /**
   * Reads a model answer.
   *
   * @param json the answer's content
   * @return its rules; empty when it proposes none
   * @throws IllegalArgumentException when the content is not an answer of the contract
   */
  public static List<ProposedRule> answer(String json) {
    try {
      Answer answer = JSON.readValue(json, Answer.class);
      if (answer == null || answer.rules() == null) {
        throw new IllegalArgumentException("a derivation answer is {\"rules\": [...]}");
      }
      return List.copyOf(answer.rules());
    } catch (JacksonException malformed) {
      throw new IllegalArgumentException(
          "the derivation answer is not in its contract: " + malformed.getOriginalMessage(),
          malformed);
    }
  }

  /**
   * Writes a value as JSON.
   *
   * @param value a rule, its law blocks or a vet report
   * @return the JSON text
   */
  public static String write(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot write the proposal", e);
    }
  }

  /**
   * Reads a stored rule.
   *
   * @param json the {@code rule} column
   * @return the rule
   */
  public static ProposedRule rule(String json) {
    return read(json, new TypeReference<>() {});
  }

  /**
   * Reads stored law blocks.
   *
   * @param json the {@code law} column
   * @return the blocks
   */
  public static List<LawBlock> law(String json) {
    return read(json, new TypeReference<>() {});
  }

  /**
   * Reads a stored vet report.
   *
   * @param json the {@code vet_report} column, or null
   * @return the report, or null
   */
  public static VetReport report(String json) {
    return json == null ? null : read(json, new TypeReference<>() {});
  }

  private static <T> T read(String json, TypeReference<T> type) {
    try {
      return JSON.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot read the stored proposal", e);
    }
  }
}
