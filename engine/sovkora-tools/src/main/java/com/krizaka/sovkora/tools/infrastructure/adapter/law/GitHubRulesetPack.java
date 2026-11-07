package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.domain.model.law.GoldenCase;
import com.krizaka.sovkora.tools.domain.model.law.LawTargetRef;
import com.krizaka.sovkora.tools.domain.model.law.PackKind;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * {@link RulesetPack} on sovkora-packs' base branch, read through the GitHub contents API with the
 * App's installation token: {@code document-validation/kinds/<kindId>.json}, the golden cases of
 * {@code document-validation/worker/fixtures/<kindId>/}, and the rulesets of {@code
 * document-validation/rulesets/<kindId>/}.
 */
public final class GitHubRulesetPack implements RulesetPack {

  static final String ROOT = "document-validation/";

  private final PackFiles files;

  /**
   * Creates the reader.
   *
   * @param github the GitHub API client, authenticated with the App's installation token
   * @param owner the owner of the rulesets repository
   * @param repository the rulesets repository
   * @param baseBranch the branch read
   */
  public GitHubRulesetPack(RestClient github, String owner, String repository, String baseBranch) {
    this.files = new PackFiles(github, owner, repository, baseBranch);
  }

  @Override
  public Optional<PackKind> kind(String kindId) {
    return files.json(kindPath(kindId)).map(kind -> toKind(kindId, kind));
  }

  @Override
  public List<GoldenCase> goldenCases(String kindId) {
    List<GoldenCase> cases = new ArrayList<>();
    for (JsonNode entry :
        files.listing(ROOT + "worker/fixtures/" + PackKind.requireKindId(kindId))) {
      String name = entry.path("name").asString("");
      if ("file".equals(entry.path("type").asString("")) && name.endsWith(".txt")) {
        cases.add(new GoldenCase(name.substring(0, name.length() - ".txt".length())));
      }
    }
    return List.copyOf(cases);
  }

  @Override
  public OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom) {
    return files
        .json(rulesetPath(kindId, effectiveFrom))
        .map(ruleset -> OptionalInt.of(ruleset.path("revision").asInt(1)))
        .orElse(OptionalInt.empty());
  }

  static String kindPath(String kindId) {
    return ROOT + "kinds/" + PackKind.requireKindId(kindId) + ".json";
  }

  static String rulesetPath(String kindId, LocalDate effectiveFrom) {
    return ROOT + "rulesets/" + PackKind.requireKindId(kindId) + "/" + effectiveFrom + ".json";
  }

  private static PackKind toKind(String kindId, JsonNode kind) {
    List<LawTargetRef> targets = new ArrayList<>();
    for (JsonNode target : kind.path("retrieval").path("targets")) {
      List<String> refs = new ArrayList<>();
      target.path("refs").forEach(ref -> refs.add(ref.asString()));
      targets.add(new LawTargetRef(target.path("instrument").asString(), refs));
    }
    Map<String, String> questions = new LinkedHashMap<>();
    kind.path("retrieval")
        .path("question")
        .properties()
        .forEach(entry -> questions.put(entry.getKey(), entry.getValue().asString()));
    return new PackKind(
        kindId,
        kind.path("jurisdiction").asString(),
        kind.path("status").asString(""),
        targets,
        questions);
  }
}
