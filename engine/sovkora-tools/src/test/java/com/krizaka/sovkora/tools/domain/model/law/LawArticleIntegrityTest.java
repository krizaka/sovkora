package com.krizaka.sovkora.tools.domain.model.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A stored version carries the hash recorded with its row; it is intact only when the corpus's own
 * normaliser, run again on the text read back, gives that hash (issue #89).
 */
class LawArticleIntegrityTest {

  private static final Instant NOW = Instant.parse("2026-10-25T06:00:00Z");

  private static LawArticle article(String text) {
    return new LawArticle(
        "QC",
        "CCQ-1991",
        "1931",
        "fr",
        LocalDate.of(1994, 1, 1),
        text,
        "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1931",
        "https://example.test/CCQ-1991.xml");
  }

  @Test
  void a_version_whose_text_still_gives_the_recorded_hash_is_intact() {
    LawArticle article = article("Le locateur doit donner un avis de 24 heures.");
    LawArticleVersion stored =
        new LawArticleVersion(
            article, NOW, NOW, null, UUID.randomUUID(), article.contentHash().hex());

    assertThat(stored.intact()).isTrue();
  }

  @Test
  void layout_alone_never_breaks_integrity_the_normaliser_is_the_corpus_own() {
    String recorded = ContentHash.of("Le locateur doit donner un avis de 24 heures.").hex();
    LawArticleVersion stored =
        new LawArticleVersion(
            article("  Le locateur\n doit\tdonner un avis de 24 heures. "),
            NOW,
            NOW,
            null,
            UUID.randomUUID(),
            recorded);

    assertThat(stored.intact()).isTrue();
  }

  @Test
  void a_text_changed_behind_the_corpus_back_is_not_intact() {
    String recorded = ContentHash.of("Le locateur doit donner un avis de 24 heures.").hex();
    LawArticleVersion stored =
        new LawArticleVersion(
            article("Le locateur doit donner un avis de 2 heures."),
            NOW,
            NOW,
            null,
            UUID.randomUUID(),
            recorded);

    assertThat(stored.intact()).isFalse();
  }

  @Test
  void a_version_built_from_an_article_just_read_records_its_own_hash() {
    LawArticle article = article("Texte.");
    LawArticleVersion read = new LawArticleVersion(article, NOW, NOW, null, null);

    assertThat(read.recordedHash()).isEqualTo(article.contentHash().hex());
    assertThat(read.intact()).isTrue();
    assertThat(read.seenAt(NOW.plusSeconds(1)).recordedHash()).isEqualTo(read.recordedHash());
  }

  @Test
  void a_recorded_hash_is_required() {
    LawArticle article = article("Texte.");
    assertThatThrownBy(() -> new LawArticleVersion(article, NOW, NOW, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void a_recorded_hash_that_is_not_a_digest_is_a_corrupted_row_never_intact() {
    LawArticle article = article("Texte.");
    String hex = article.contentHash().hex();

    // A corrupted row fails the check (LAW_INTEGRITY_ERROR), it is not a bad question (400).
    assertThat(new LawArticleVersion(article, NOW, NOW, null, null, "ABC").intact()).isFalse();
    assertThat(new LawArticleVersion(article, NOW, NOW, null, null, "").intact()).isFalse();
    assertThat(
            new LawArticleVersion(article, NOW, NOW, null, null, hex.toUpperCase(Locale.ROOT))
                .intact())
        .isFalse();
  }

  @Test
  void a_quote_is_verified_under_the_same_normalisation_and_nothing_else() {
    String text = "Le locateur doit donner un avis de 24 heures à son locataire.";

    assertThat(QuoteCheck.of(text, "avis de 24 heures")).isEqualTo(QuoteCheck.VERIFIED);
    assertThat(QuoteCheck.of(text, " avis  de\n24 heures ")).isEqualTo(QuoteCheck.VERIFIED);
    // NFD « à » (a + combining grave) is the same text once composed.
    assertThat(QuoteCheck.of(text, "à son locataire")).isEqualTo(QuoteCheck.VERIFIED);
    assertThat(QuoteCheck.of(text, "avis de 48 heures")).isEqualTo(QuoteCheck.NOT_IN_ARTICLE);
    // No case folding, no stemming: a quote is the article's words or it is not.
    assertThat(QuoteCheck.of(text, "AVIS DE 24 HEURES")).isEqualTo(QuoteCheck.NOT_IN_ARTICLE);
  }

  @Test
  void a_blank_quote_is_not_a_question() {
    assertThatThrownBy(() -> QuoteCheck.of("Texte.", " \n "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
