package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of document as the pack declares it ({@code document-validation/kinds/<kindId>.json}):
 * what a derivation of it retrieves and asks.
 *
 * @param kindId the kind
 * @param jurisdiction its jurisdiction
 * @param status {@code KNOWN}, {@code PROMOTED} or {@code RETIRED}
 * @param targets the articles a retrieval of it reads; empty when it declares none
 * @param questions the retrieval question by language ({@code fr}, {@code en})
 */
public record PackKind(
    String kindId,
    String jurisdiction,
    String status,
    List<LawTargetRef> targets,
    Map<String, String> questions) {

  /** A kind id, {@code <jurisdiction>-<name>}, as the pack's kind schema writes it. */
  private static final Pattern KIND_ID =
      Pattern.compile("[a-z]{2}(?:-[a-z]{3})?-[a-z0-9][a-z0-9-]{0,76}");

  /**
   * Checks a kind id before it names a file: nothing but the pack's own shape of an id.
   *
   * @param kindId the id
   * @return the id
   * @throws IllegalArgumentException when it is not a kind id
   */
  public static String requireKindId(String kindId) {
    if (kindId == null || !KIND_ID.matcher(kindId).matches()) {
      throw new IllegalArgumentException("not a kind id: " + kindId);
    }
    return kindId;
  }

  /** Copies the parts. */
  public PackKind {
    Objects.requireNonNull(kindId, "kindId");
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    targets = targets == null ? List.of() : List.copyOf(targets);
    questions = questions == null ? Map.of() : Map.copyOf(questions);
  }
}
