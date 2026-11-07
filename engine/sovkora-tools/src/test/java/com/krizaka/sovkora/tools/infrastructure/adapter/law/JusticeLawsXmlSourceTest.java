package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JusticeLawsXmlSourceTest {

  @Test
  void reads_both_languages_of_each_act_and_fingerprints_the_files() throws Exception {
    byte[] english = resource("/law/E-15-slice.xml");
    String french =
        new String(english, StandardCharsets.UTF_8)
            .replace("xml:lang=\"en\"", "xml:lang=\"fr\" lims:current-date=\"2026-10-20\"");
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://laws-lois.justice.gc.ca/eng/XML/E-15.xml"))
        .andRespond(withSuccess(english, MediaType.APPLICATION_XML));
    server
        .expect(requestTo("https://laws-lois.justice.gc.ca/fra/XML/E-15.xml"))
        .andRespond(withSuccess(french, MediaType.APPLICATION_XML));
    JusticeLawsXmlSource source =
        new JusticeLawsXmlSource(builder.build(), new LawStatuteXmlParser());

    LawSnapshot snapshot =
        source.fetch("CA", List.of(LawTarget.wholeInstrument("CA", "E-15")), Optional.empty());

    server.verify();
    assertThat(source.sourceKey()).isEqualTo("justice-laws");
    assertThat(source.serves("CA")).isTrue();
    assertThat(source.serves("QC")).isFalse();
    assertThat(snapshot.languages()).containsExactlyInAnyOrder("fr", "en");
    assertThat(snapshot.articles()).extracting(LawArticle::language).contains("en", "fr");
    assertThat(snapshot.datasetSha256())
        .isEqualTo(
            Sha256.hex(
                ("https://laws-lois.justice.gc.ca/eng/XML/E-15.xml "
                        + Sha256.hex(english)
                        + "\nhttps://laws-lois.justice.gc.ca/fra/XML/E-15.xml "
                        + Sha256.hex(french.getBytes(StandardCharsets.UTF_8))
                        + "\n")
                    .getBytes(StandardCharsets.UTF_8)));
    assertThat(snapshot.consolidatedTo()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(snapshot.notModified()).isFalse();
  }

  private static byte[] resource(String name) throws Exception {
    try (InputStream in = JusticeLawsXmlSourceTest.class.getResourceAsStream(name)) {
      return Objects.requireNonNull(in, name).readAllBytes();
    }
  }
}
