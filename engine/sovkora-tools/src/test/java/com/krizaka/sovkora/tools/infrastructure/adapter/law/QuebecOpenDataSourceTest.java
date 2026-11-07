package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QuebecOpenDataSourceTest {

  private static final String DATASET = "https://www.donneesquebec.test/lois.zip";
  private static final String DATED = "https://www.donneesquebec.test/download/20261021_lois.zip";

  @Test
  void reads_only_the_targeted_instruments_from_the_snapshot_and_never_parses_the_others()
      throws Exception {
    byte[] ccq = resource("/law/CCQ-1991_FR-slice.xml");
    byte[] zip =
        zip(
            "Statutes\\CCQ-1991\\FR\\CCQ-1991_FR.xml",
            ccq,
            // not targeted: if it were parsed, this would not be XML
            "Statutes\\M-30\\FR\\M-30_FR.xml",
            "not xml".getBytes(StandardCharsets.UTF_8));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATASET))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));
    QuebecOpenDataSource source =
        new QuebecOpenDataSource(builder.build(), DATASET, new LawStatuteXmlParser());

    LawSnapshot snapshot =
        source.fetch("QC", List.of(LawTarget.wholeInstrument("QC", "CCQ-1991")), Optional.empty());
    List<LawArticle> articles = snapshot.articles();

    server.verify();
    assertThat(snapshot.datasetUrl()).isEqualTo(DATASET);
    assertThat(snapshot.datasetSha256()).isEqualTo(Sha256.hex(zip));
    assertThat(snapshot.languages()).containsExactlyInAnyOrder("fr", "en");
    assertThat(snapshot.notModified()).isFalse();
    assertThat(source.sourceKey()).isEqualTo("donnees-quebec");
    assertThat(articles).isNotEmpty().allMatch(a -> "CCQ-1991".equals(a.instrument()));
    assertThat(articles).allMatch(a -> "fr".equals(a.language()));
    assertThat(articles).extracting(LawArticle::ref).contains("1942");
    assertThat(source.serves("QC")).isTrue();
    assertThat(source.serves("CA")).isFalse();
  }

  @Test
  void keeps_the_etag_and_reads_the_snapshot_date_from_the_file_name() throws Exception {
    byte[] zip =
        zip("Statutes\\CCQ-1991\\FR\\CCQ-1991_FR.xml", resource("/law/CCQ-1991_FR-slice.xml"));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATED))
        .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM).header("ETag", "\"v42\""));
    QuebecOpenDataSource source =
        new QuebecOpenDataSource(builder.build(), DATED, new LawStatuteXmlParser());

    LawSnapshot snapshot =
        source.fetch("QC", List.of(LawTarget.wholeInstrument("QC", "CCQ-1991")), Optional.empty());

    assertThat(snapshot.datasetEtag()).isEqualTo("\"v42\"");
    assertThat(snapshot.consolidatedTo()).isEqualTo(LocalDate.of(2026, 10, 21));
  }

  @Test
  void asks_conditionally_and_reads_nothing_when_the_publisher_says_not_modified()
      throws Exception {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATED))
        .andExpect(header("If-None-Match", "\"v42\""))
        .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
    QuebecOpenDataSource source =
        new QuebecOpenDataSource(builder.build(), DATED, new LawStatuteXmlParser());

    LawSnapshot snapshot =
        source.fetch(
            "QC", List.of(LawTarget.wholeInstrument("QC", "CCQ-1991")), Optional.of("\"v42\""));

    server.verify();
    assertThat(snapshot.notModified()).isTrue();
    assertThat(snapshot.articles()).isEmpty();
    assertThat(snapshot.datasetEtag()).isEqualTo("\"v42\"");
  }

  @Test
  void an_error_status_or_an_empty_body_fails_the_read() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(DATED)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(DATED))
        .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));
    QuebecOpenDataSource source =
        new QuebecOpenDataSource(builder.build(), DATED, new LawStatuteXmlParser());
    List<LawTarget> targets = List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"));

    assertThatThrownBy(() -> source.fetch("QC", targets, Optional.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("503");
    assertThatThrownBy(() -> source.fetch("QC", targets, Optional.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  private static byte[] resource(String name) throws Exception {
    try (InputStream in = QuebecOpenDataSourceTest.class.getResourceAsStream(name)) {
      return Objects.requireNonNull(in, name).readAllBytes();
    }
  }

  private static byte[] zip(Object... nameAndBytes) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (int i = 0; i < nameAndBytes.length; i += 2) {
        zip.putNextEntry(new ZipEntry((String) nameAndBytes[i]));
        zip.write((byte[]) nameAndBytes[i + 1]);
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}
