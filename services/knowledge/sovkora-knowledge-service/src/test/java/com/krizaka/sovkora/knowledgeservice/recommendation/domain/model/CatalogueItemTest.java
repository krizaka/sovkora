package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogueItemTest {

  private static CatalogueItem item(String title, List<String> tags) {
    return new CatalogueItem("v1", "c1", title, "desc", tags, "en", 10, Fixtures.NOW);
  }

  @Test
  @DisplayName("tags are normalised (case, blanks, duplicates) before they are embedded")
  void normalisesTags() {
    assertThat(item("t", List.of(" Jazz", "jazz", "", "LIVE")).tags())
        .containsExactly("jazz", "live");
  }

  @Test
  @DisplayName("the hash changes with the text and only with it")
  void hashFollowsText() {
    assertThat(item("a", List.of("x")).contentHash())
        .isEqualTo(item("a", List.of("X")).contentHash());
    assertThat(item("a", List.of("x")).contentHash())
        .isNotEqualTo(item("b", List.of("x")).contentHash());
  }

  @Test
  @DisplayName("an item with neither title nor description has nothing to embed")
  void needsText() {
    assertThatThrownBy(
            () -> new CatalogueItem("v1", "c1", " ", null, null, null, null, Fixtures.NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("the document puts the title first, then the tags, then the description")
  void document() {
    assertThat(item("Jazz trio", List.of("jazz")).document()).isEqualTo("Jazz trio\n#jazz\ndesc");
  }

  @Test
  @DisplayName("the language is a BCP-47 tag, checked without a backtracking group")
  void checksLanguageTag() {
    for (String ok : List.of("en", "fr-CA", "zh-Hant-TW", "eng")) {
      assertThat(withLanguage(ok).language()).isEqualTo(ok);
    }
    for (String bad : List.of("e", "english", "en-", "en--CA", "en-C", "en-CA_x", "-en")) {
      assertThatThrownBy(() -> withLanguage(bad)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static CatalogueItem withLanguage(String language) {
    return new CatalogueItem("v1", "c1", "t", "d", List.of(), language, 10, Fixtures.NOW);
  }
}
