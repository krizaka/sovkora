package com.krizaka.sovkora.tools.domain.model.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentHashTest {

  @Test
  void layoutAndComposedCharactersDoNotChangeTheHash() {
    String composed = "État des lieux, le locateur doit donner un avis de visite.";
    String layout = "  État\n\tdes lieux, le locateur doit donner un avis  de visite.\n";
    String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
    assertThat(ContentHash.of(layout)).isEqualTo(ContentHash.of(composed));
    assertThat(ContentHash.of(decomposed)).isEqualTo(ContentHash.of(composed));
  }

  @Test
  void a_word_changed_changes_the_hash() {
    assertThat(ContentHash.of("un avis de trois mois"))
        .isNotEqualTo(ContentHash.of("un avis de quatre mois"));
  }

  @Test
  void the_hash_is_64_lowercase_hex() {
    assertThat(ContentHash.of("x").hex()).matches("[0-9a-f]{64}");
  }

  @Test
  void a_malformed_digest_is_refused() {
    assertThatThrownBy(() -> new ContentHash("ABC")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_target_expands_ranges_and_keeps_single_references() {
    LawTarget target = LawTarget.of("QC", "CCQ-1991", List.of("1851-2000"));
    assertThat(target.refs()).hasSize(150).first().isEqualTo("1851");
    assertThat(LawTarget.of("QC", "T-0.1", List.of("16", "201")).refs())
        .containsExactly("16", "201");
    assertThatThrownBy(() -> LawTarget.of("QC", "CCQ-1991", List.of("2000-1851")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
