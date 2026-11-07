package com.krizaka.sovkora.core.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Typography never decides whether a passage was read (ADR-087 §3, CTO review). */
class QuoteNormalizerTest {

  private static final String FR =
      "Le locateur ne peut exiger « une somme d’argent » autre que le loyer ; il"
          + " répond de l’œuvre : c’est la règle.";

  @Test
  @DisplayName(
      "FR: « guillemets », the curly apostrophe and the narrow no-break space match plain typing")
  void frenchTypography() {
    assertThat(QuoteNormalizer.contains(FR, "\"une somme d'argent\" autre que le loyer ; il"))
        .isTrue();
    assertThat(QuoteNormalizer.contains(FR, "il répond de l'oeuvre : c'est la règle")).isTrue();
  }

  @Test
  @DisplayName("FR: an NFD text matches an NFC quote, and the reverse")
  void unicodeForms() {
    String nfd = Normalizer.normalize(FR, Normalizer.Form.NFD);
    assertThat(QuoteNormalizer.contains(nfd, "répond de l’œuvre")).isTrue();
    assertThat(QuoteNormalizer.contains(FR, Normalizer.normalize("répond", Normalizer.Form.NFD)))
        .isTrue();
  }

  @Test
  @DisplayName("FR: the ligatures œ and æ read as oe and ae")
  void ligatures() {
    assertThat(QuoteNormalizer.contains("un curriculum vitæ et un cœur", "vitae et un coeur"))
        .isTrue();
  }

  @Test
  @DisplayName("EN: curly quotes, dashes, the ellipsis and case are not differences")
  void englishTypography() {
    String text = "The tenant’s “first payment” — and only that… is due.";
    assertThat(
            QuoteNormalizer.contains(
                text, "the TENANT'S \"first payment\" - and only that... is due"))
        .isTrue();
  }

  @Test
  @DisplayName("a changed word is a different passage, and an empty quote is never found")
  void wordsStillCount() {
    assertThat(QuoteNormalizer.contains(FR, "ne peut pas exiger")).isFalse();
    assertThat(QuoteNormalizer.contains(FR, "   ")).isFalse();
  }
}
