package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher;
import com.krizaka.sovkora.tools.domain.model.law.PinnedArticle;
import com.krizaka.sovkora.tools.domain.model.law.PromotedRule;
import com.krizaka.sovkora.tools.domain.model.law.RulesetPromotion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Opens the promotion pull request in sovkora-packs with the GitHub App: one commit on a new branch
 * from the base (the ruleset, the pinned article versions, the kind set to {@code PROMOTED}), then
 * the pull request. It never writes the base branch: the merge, by a person, is the promotion.
 */
public final class GitHubRulesetPromotionPublisher implements RulesetPromotionPublisher {

  private final PackFiles files;
  private final Supplier<UUID> ids;

  /**
   * Creates the publisher.
   *
   * @param github the GitHub API client, authenticated with the App's installation token
   * @param owner the owner of the rulesets repository
   * @param repository the rulesets repository
   * @param baseBranch the branch the pull requests are opened against, never written
   */
  public GitHubRulesetPromotionPublisher(
      RestClient github, String owner, String repository, String baseBranch) {
    this(github, owner, repository, baseBranch, UUID::randomUUID);
  }

  GitHubRulesetPromotionPublisher(
      RestClient github, String owner, String repository, String baseBranch, Supplier<UUID> ids) {
    this.files = new PackFiles(github, owner, repository, baseBranch);
    this.ids = ids;
  }

  @Override
  public Opened open(RulesetPromotion promotion) {
    String kindPath = GitHubRulesetPack.kindPath(promotion.kindId());
    JsonNode kind =
        files
            .json(kindPath)
            .orElseThrow(
                () -> new IllegalStateException("sovkora-packs has no kind " + promotion.kindId()));
    String label = kind.path("label").path("fr").asString(promotion.kindId());
    String branch =
        "law-promote/"
            + promotion.kindId()
            + "/"
            + promotion.effectiveFrom()
            + "-r"
            + promotion.revision()
            + "-"
            + ids.get().toString().substring(0, 8);
    if (branch.equals(files.baseBranch())) {
      throw new IllegalStateException("a promotion never writes the base branch");
    }

    Map<String, String> changes = new LinkedHashMap<>();
    changes.put(
        GitHubRulesetPack.rulesetPath(promotion.kindId(), promotion.effectiveFrom()),
        PromotedRulesetJson.ruleset(promotion, label));
    for (PinnedArticle article : promotion.articles()) {
      changes.put(
          GitHubRulesetPack.ROOT + "law/" + article.fileName(),
          PromotedRulesetJson.article(article));
    }
    changes.put(kindPath, PromotedRulesetJson.promotedKind(kind, promotion.kindId()));

    String repo = files.repo();
    Map<?, ?> base =
        files
            .github()
            .get()
            .uri(repo + "/git/ref/heads/{base}", files.baseBranch())
            .retrieve()
            .body(Map.class);
    String baseSha = (String) ((Map<?, ?>) base.get("object")).get("sha");
    Map<?, ?> baseCommit =
        files.github().get().uri(repo + "/git/commits/{sha}", baseSha).retrieve().body(Map.class);
    String baseTree = (String) ((Map<?, ?>) baseCommit.get("tree")).get("sha");

    List<Map<String, String>> tree = new ArrayList<>();
    changes.forEach(
        (path, content) ->
            tree.add(Map.of("path", path, "mode", "100644", "type", "blob", "content", content)));
    Map<?, ?> newTree =
        files
            .github()
            .post()
            .uri(repo + "/git/trees")
            .body(Map.of("base_tree", baseTree, "tree", tree))
            .retrieve()
            .body(Map.class);
    String title = title(promotion);
    Map<?, ?> commit =
        files
            .github()
            .post()
            .uri(repo + "/git/commits")
            .body(Map.of("message", title, "tree", newTree.get("sha"), "parents", List.of(baseSha)))
            .retrieve()
            .body(Map.class);
    files
        .github()
        .post()
        .uri(repo + "/git/refs")
        .body(Map.of("ref", "refs/heads/" + branch, "sha", commit.get("sha")))
        .retrieve()
        .toBodilessEntity();
    Map<?, ?> pull =
        files
            .github()
            .post()
            .uri(repo + "/pulls")
            .body(
                Map.of(
                    "title",
                    title,
                    "head",
                    branch,
                    "base",
                    files.baseBranch(),
                    "body",
                    body(promotion)))
            .retrieve()
            .body(Map.class);
    return new Opened((String) pull.get("html_url"), branch);
  }

  private static String title(RulesetPromotion promotion) {
    return "feat(document-validation): promote "
        + promotion.kindId()
        + " "
        + promotion.effectiveFrom()
        + " revision "
        + promotion.revision()
        + " (ADR-089 §5)";
  }

  private static String body(RulesetPromotion promotion) {
    StringBuilder body =
        new StringBuilder()
            .append("Opened by the engine from the console's review queue (ADR-089 §5, step 4).")
            .append(" The merge of this pull request is the promotion; the pack's release makes it")
            .append(" executable.\n\n")
            .append("- Ruleset: `")
            .append(promotion.rulesetPath())
            .append("`, version ")
            .append(promotion.effectiveFrom())
            .append(", revision ")
            .append(promotion.revision())
            .append("\n- Review: `")
            .append(RulesetPromotion.REVIEW_STATUS)
            .append("`; `")
            .append(RulesetPromotion.LAWYER_MARK)
            .append("` stays until a lawyer signs it off.\n- Proposals: ")
            .append(promotion.provenance().proposalIds())
            .append("\n\n| Rule | Severity | Article | Quote |\n|:---|:---|:---|:---|\n");
    for (PromotedRule rule : promotion.rules()) {
      rule.law()
          .forEach(
              law ->
                  body.append("| `")
                      .append(rule.rule().ruleId())
                      .append("` | ")
                      .append(rule.rule().severity())
                      .append(" | [")
                      .append(law.instrument())
                      .append(" art. ")
                      .append(law.ref())
                      .append("](")
                      .append(law.officialUrl())
                      .append(") | ")
                      .append(law.quote().replace("|", "\\|").replace("\n", " "))
                      .append(" |\n"));
    }
    return body.append(
            "\nThe pack's CI runs `test_rules.py`, `test_golden.py` and `test_identify.py`.\n")
        .toString();
  }
}
