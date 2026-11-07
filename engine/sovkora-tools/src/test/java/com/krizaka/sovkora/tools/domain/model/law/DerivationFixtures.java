package com.krizaka.sovkora.tools.domain.model.law;

import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.ProposedRulesJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The data of the derivation tests: art. 1931 to 1933 of the Civil Code of Québec as the corpus
 * holds them (parsed from the official XML), the recorded answers of the derivation model for the
 * visit notice, and one planted proposal per reason of {@code vet}. The JSON files mirror
 * sovkora-packs {@code document-validation/derivation/}, where the pack's own worker proves each
 * planted case fails for its reason.
 */
public final class DerivationFixtures {

  /** The day of the fictional visit notice the derivation is run for. */
  public static final LocalDate NOTICE_DAY = LocalDate.parse("2026-10-20");

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private DerivationFixtures() {}

  /** One planted case: the rules given to the vet, the one planted, and its reason. */
  public record Planted(
      String name,
      VetReason reason,
      String plantedRuleId,
      int maxRules,
      List<ProposedRule> rules) {}

  /**
   * Art. 1931 to 1933, French, as the parser reads the official file.
   *
   * @return the articles
   */
  public static List<LawArticle> visitArticles() {
    try (InputStream in = resource("/law/CCQ-1991_FR-visit-notice.xml")) {
      return new LawStatuteXmlParser()
          .parseQuebec(in, "CCQ-1991", "fr", "https://donneesquebec.test/20261021_lois.zip");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The corpus on the notice's day: art. 1931 to 1933 and nothing else.
   *
   * @return the view the vet reads
   */
  public static InForceArticles visitCorpus() {
    List<LawArticle> articles = visitArticles();
    return (instrument, ref, language) ->
        articles.stream()
            .filter(a -> a.instrument().equals(instrument))
            .filter(a -> a.ref().equals(ref) && a.language().equals(language))
            .filter(a -> !a.inForceFrom().isAfter(NOTICE_DAY))
            .findFirst()
            .map(
                a ->
                    new StatuteVersion(
                        a.text(), a.contentHash().hex(), a.inForceFrom(), a.officialUrl(), null));
  }

  /**
   * The recorded answers of the derivation model for the visit notice, by article.
   *
   * @return the rules each call proposed, keyed by the article reference
   */
  public static Map<String, List<ProposedRule>> visitAnswers() {
    JsonNode answers = json("/law/derivation/qc-visit-notice.fr.answers.json").get("answers");
    Map<String, List<ProposedRule>> byRef = new LinkedHashMap<>();
    answers
        .properties()
        .forEach(e -> byRef.put(e.getKey(), ProposedRulesJson.answer(e.getValue().toString())));
    return byRef;
  }

  /**
   * A recorded answer citing a rent article the corpus does not hold.
   *
   * @return its rules
   */
  public static List<ProposedRule> outsideCorpusAnswer() {
    return ProposedRulesJson.answer(
        text("/law/derivation/qc-visit-notice.fr.outside-corpus.answer.json"));
  }

  /**
   * Every planted case.
   *
   * @return one per file of {@code law/derivation/planted}
   */
  public static List<Planted> planted() {
    try (Stream<Path> files = Files.list(Path.of(url("/law/derivation/planted")))) {
      return files.sorted().map(DerivationFixtures::planted).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Planted planted(Path file) {
    try {
      JsonNode node = JSON.readTree(Files.readString(file));
      return new Planted(
          file.getFileName().toString(),
          VetReason.valueOf(node.get("reason").asString()),
          node.get("plantedRuleId").asString(),
          Optional.ofNullable(node.get("maxRulesPerKind")).map(JsonNode::asInt).orElse(30),
          ProposedRulesJson.answer(node.toString()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode json(String name) {
    return JSON.readTree(text(name));
  }

  private static String text(String name) {
    try (InputStream in = resource(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static InputStream resource(String name) {
    return Objects.requireNonNull(DerivationFixtures.class.getResourceAsStream(name), name);
  }

  private static java.net.URI url(String name) {
    try {
      return Objects.requireNonNull(DerivationFixtures.class.getResource(name), name).toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
