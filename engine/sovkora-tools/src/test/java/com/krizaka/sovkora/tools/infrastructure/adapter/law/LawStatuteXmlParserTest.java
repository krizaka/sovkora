package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** The parsers on recorded XML: slices of the Données Québec and Justice Canada files. */
class LawStatuteXmlParserTest {

  private final LawStatuteXmlParser parser = new LawStatuteXmlParser();

  @Test
  void a_quebec_statute_gives_each_section_its_in_force_date_and_drops_the_repealed_ones()
      throws Exception {
    List<LawArticle> articles =
        parse(
            "/law/CCQ-1991_FR-slice.xml",
            in -> parser.parseQuebec(in, "CCQ-1991", "fr", "https://donnees.test/ccq.xml"));

    assertThat(articles)
        .extracting(LawArticle::ref)
        .containsExactly("1851", "1855", "1904", "1942", "1943", "2000");
    LawArticle art1942 = articles.get(3);
    assertThat(art1942.inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1));
    assertThat(art1942.text()).contains("au moins trois mois").contains("pas plus de six mois");
    assertThat(art1942.text()).doesNotContain("c. 64, a. 1942");
    assertThat(art1942.officialUrl())
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1942");
    assertThat(art1942.sourceUrl()).isEqualTo("https://donnees.test/ccq.xml");
  }

  @Test
  void a_federal_act_gives_each_section_its_label_and_in_force_date() throws Exception {
    List<LawArticle> articles =
        parse(
            "/law/E-15-slice.xml",
            in -> parser.parseJustice(in, "E-15", "en", "https://laws.test/E-15.xml"));

    assertThat(articles).extracting(LawArticle::ref).contains("1", "2");
    LawArticle shortTitle = articles.getFirst();
    assertThat(shortTitle.inForceFrom()).isEqualTo(LocalDate.of(2003, 1, 1));
    assertThat(shortTitle.text()).contains("This Act may be cited as the Excise Tax Act");
    assertThat(shortTitle.officialUrl())
        .isEqualTo("https://laws-lois.justice.gc.ca/eng/acts/E-15/section-1.html");
  }

  @Test
  void a_section_without_its_date_fails_loudly() {
    String xml =
        "<LegislativeDocument><Statute><Body>"
            + "<Section Code=\"se:1942\"><Label>1942</Label><Text>Texte.</Text></Section>"
            + "</Body></Statute></LegislativeDocument>";
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                parser.parseQuebec(
                    new java.io.ByteArrayInputStream(xml.getBytes()),
                    "CCQ-1991",
                    "fr",
                    "https://donnees.test/x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("date-eev");
  }

  private interface Reader {
    List<LawArticle> read(InputStream in) throws Exception;
  }

  private List<LawArticle> parse(String resource, Reader reader) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      return reader.read(Objects.requireNonNull(in, resource));
    }
  }
}
