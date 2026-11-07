package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.domain.model.law.PinnedArticle;
import com.krizaka.sovkora.tools.domain.model.law.PinnedLaw;
import com.krizaka.sovkora.tools.domain.model.law.PromotedRule;
import com.krizaka.sovkora.tools.domain.model.law.RulesetPromotion;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The files a promotion writes in sovkora-packs, in the pack's own shapes: the ruleset ({@code
 * rulesets/<kindId>/<effectiveFrom>.json}), each pinned article version ({@code law/}, read by the
 * worker's {@code citations.py}) and the kind set to {@code PROMOTED}. Two spaces, one key per
 * line, a final newline, as the pack's files are written.
 */
final class PromotedRulesetJson {

  private static final DefaultIndenter TWO_SPACES = new DefaultIndenter("  ", "\n");

  private static final ObjectWriter WRITER =
      PackFiles.MAPPER
          .writer()
          .with(
              new DefaultPrettyPrinter(
                      Separators.createDefaultInstance()
                          .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                          .withArrayEmptySeparator("")
                          .withObjectEmptySeparator(""))
                  .withArrayIndenter(TWO_SPACES)
                  .withObjectIndenter(TWO_SPACES));

  private PromotedRulesetJson() {}

  /** The ruleset revision, its law blocks pinned and its provenance. */
  static String ruleset(RulesetPromotion promotion, String label) {
    ObjectNode root = PackFiles.MAPPER.createObjectNode();
    root.put("rulesetId", promotion.kindId());
    root.put("label", label);
    root.put("jurisdiction", promotion.jurisdiction());
    root.put("version", promotion.effectiveFrom().toString());
    root.put("effectiveFrom", promotion.effectiveFrom().toString());
    root.putNull("effectiveTo");
    root.put("source", source(promotion));
    root.put("revision", promotion.revision());
    if (promotion.revisionNote() != null) {
      root.put("revisionNote", promotion.revisionNote());
    }
    root.put("reviewStatus", RulesetPromotion.REVIEW_STATUS);
    root.put(
        "reviewNote",
        "Relu en interne dans la file de revue de la console (ADR-089 §5) : chaque règle, sa"
            + " citation et ses exemples. "
            + RulesetPromotion.LAWYER_MARK
            + " : à faire relire par un avocat ou une avocate avant tout usage autre que la"
            + " démonstration.");
    ArrayNode rules = root.putArray("rules");
    for (PromotedRule rule : promotion.rules()) {
      rules.add(rule(rule));
    }
    ObjectNode provenance = root.putObject("provenance");
    RulesetPromotion.Provenance from = promotion.provenance();
    from.derivationRunIds()
        .forEach(id -> provenance.withArray("derivationRunIds").add(id.toString()));
    from.proposalIds().forEach(id -> provenance.withArray("proposalIds").add(id.toString()));
    from.reviewedBy().forEach(who -> provenance.withArray("reviewedBy").add(who));
    if (from.reviewedAt() != null) {
      provenance.put("reviewedAt", from.reviewedAt().toString());
    }
    from.lawImportIds().forEach(id -> provenance.withArray("lawImportIds").add(id.toString()));
    return write(root);
  }

  /** One pinned article version, as {@code citations.py} reads it. */
  static String article(PinnedArticle article) {
    ObjectNode node = PackFiles.MAPPER.createObjectNode();
    node.put("instrument", article.instrument());
    node.put("ref", article.ref());
    node.put("language", article.language());
    node.put("inForceFrom", article.inForceFrom().toString());
    node.put("contentHash", article.contentHash());
    node.put("officialUrl", article.officialUrl());
    node.put(
        "source",
        "Corpus du droit daté et empreint (ADR-089 §2), law_import "
            + article.importId()
            + " (texte public de la loi).");
    node.put("text", article.text());
    return write(node);
  }

  /** The kind file with its status {@code PROMOTED} and its ruleset named; the rest untouched. */
  static String promotedKind(JsonNode kind, String rulesetId) {
    ObjectNode copy = (ObjectNode) kind.deepCopy();
    copy.put("ruleset", rulesetId);
    copy.put("status", "PROMOTED");
    return write(copy);
  }

  private static ObjectNode rule(PromotedRule promoted) {
    ObjectNode node =
        (ObjectNode) PackFiles.MAPPER.readTree(ProposedRulesJson.write(promoted.rule()));
    ArrayNode law = node.putArray("law");
    for (PinnedLaw block : promoted.law()) {
      ObjectNode b = law.addObject();
      b.put("instrument", block.instrument());
      b.put("ref", block.ref());
      b.put("language", block.language());
      b.put("officialUrl", block.officialUrl());
      b.put("inForceFrom", block.inForceFrom().toString());
      b.put("contentHash", block.contentHash());
      b.put("quote", block.quote());
    }
    return node;
  }

  private static String source(RulesetPromotion promotion) {
    Set<String> cited = new LinkedHashSet<>();
    for (PromotedRule rule : promotion.rules()) {
      for (PinnedLaw block : rule.law()) {
        cited.add(block.instrument() + " art. " + block.ref() + " (" + block.inForceFrom() + ")");
      }
    }
    return "Corpus du droit daté et empreint (ADR-089) : "
        + cited.stream().collect(Collectors.joining(", "));
  }

  private static String write(JsonNode node) {
    return WRITER.writeValueAsString(node) + "\n";
  }
}
