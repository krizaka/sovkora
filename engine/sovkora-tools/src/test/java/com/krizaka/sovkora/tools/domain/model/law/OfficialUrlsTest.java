package com.krizaka.sovkora.tools.domain.model.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OfficialUrlsTest {

  @Test
  void a_quebec_article_is_cited_on_legis_quebec_in_its_language() {
    assertThat(OfficialUrls.of("QC", "CCQ-1991", "fr", "1931"))
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1931");
    assertThat(OfficialUrls.of("QC", "CCQ-1991", "en", "1931"))
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/en/version/lc/CCQ-1991?code=se:1931");
  }

  @Test
  void a_federal_article_is_cited_on_justice_laws_in_its_language() {
    assertThat(OfficialUrls.of("CA", "E-15", "en", "165"))
        .isEqualTo("https://laws-lois.justice.gc.ca/eng/acts/E-15/section-165.html");
    assertThat(OfficialUrls.of("CA", "E-15", "fr", "165"))
        .isEqualTo("https://laws-lois.justice.gc.ca/fra/lois/E-15/article-165.html");
  }

  @Test
  void the_template_is_data_that_resolves_per_article() {
    String template = OfficialUrls.template("QC", "T-0.1");

    assertThat(template).contains("{lang}", "{ref}");
    assertThat(OfficialUrls.resolve(template, "fr", "16"))
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/version/lc/T-0.1?code=se:16");
  }

  @Test
  void a_jurisdiction_without_a_publisher_is_refused() {
    assertThatThrownBy(() -> OfficialUrls.of("FR", "X", "fr", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
