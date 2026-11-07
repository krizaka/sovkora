package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.application.service.LawImportService;
import com.krizaka.sovkora.tools.application.service.LawManualImportService;
import com.krizaka.sovkora.tools.application.service.LawStatuteLookupService;
import com.krizaka.sovkora.tools.application.service.LawWatchService;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.LawWatchReport;
import com.krizaka.sovkora.tools.domain.model.law.ManualLawFile;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import com.krizaka.sovkora.tools.domain.model.law.StatuteLookup;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawXmlFileReader;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.QuebecOpenDataSource;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawImportRepository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * The importers end to end on PostgreSQL: the recorded XML through the watcher (the Données Québec
 * zip, conditional on its ETag) and through the manual import, into the real law tables, then
 * {@code lookupStatute} at past days. The instrument is {@code CCQ-1991}, so the tests share one
 * corpus and run in order of the story: first read, unchanged, amended, reverted, manual.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaLawCorpusStore.class)
class LawCorpusImportIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);
  private static final String DATASET = "https://www.donneesquebec.test/download/20261021_lois.zip";

  @Autowired private JpaLawCorpusStore store;
  @Autowired private LawImportRepository imports;
  @Autowired private JdbcTemplate jdbc;

  private final LawSettings settings = new CcqSettings();
  private LawStatuteLookupService lookups;

  @BeforeEach
  void cleanCorpus() {
    LawTestSchema.apply(jdbc);
    jdbc.update("delete from law_article_version");
    jdbc.update("delete from law_instrument");
    jdbc.update("delete from law_import");
    lookups = new LawStatuteLookupService(store, settings, CLOCK);
  }

  private LawWatchReport watch(String resource, String etag) throws Exception {
    byte[] zip = zip("Statutes\\CCQ-1991\\FR\\CCQ-1991_FR.xml", resource(resource));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATASET))
        .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM).header("ETag", etag));
    LawSource source =
        new QuebecOpenDataSource(builder.build(), DATASET, new LawStatuteXmlParser());
    LawImportService importer = new LawImportService(store, settings, null, CLOCK);
    LawWatchReport report = new LawWatchService(List.of(source), settings, importer).run();
    server.verify();
    return report;
  }

  private LawWatchReport watchNotModified(String knownEtag) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(DATASET))
        .andExpect(header("If-None-Match", knownEtag))
        .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
    LawSource source =
        new QuebecOpenDataSource(builder.build(), DATASET, new LawStatuteXmlParser());
    LawWatchReport report =
        new LawWatchService(
                List.of(source), settings, new LawImportService(store, settings, null, CLOCK))
            .run();
    server.verify();
    return report;
  }

  @Test
  @DisplayName(
      "new, unchanged, changed, removed then reverted: every state is a dated row and an import row")
  void theStoryOfOneArticle() throws Exception {
    LawWatchReport first = watch("/law/CCQ-1991_FR-slice.xml", "\"v1\"");
    assertThat(first.failures()).isEmpty();
    assertThat(first.stored()).isEqualTo(6);
    LawImportEntity firstRow = imports.findById(first.imports().getFirst()).orElseThrow();
    assertThat(firstRow.getMode()).isEqualTo("AUTOMATED");
    assertThat(firstRow.getSourceKey()).isEqualTo("donnees-quebec");
    assertThat(firstRow.getDatasetUrl()).isEqualTo(DATASET);
    assertThat(firstRow.getDatasetEtag()).isEqualTo("\"v1\"");
    assertThat(firstRow.getDatasetSha256()).hasSize(64);
    assertThat(firstRow.getConsolidatedTo()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(firstRow.getArticlesNew()).isEqualTo(6);
    assertThat(firstRow.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(firstRow.getFinishedAt()).isNotNull();

    LawWatchReport notModified = watchNotModified("\"v1\"");
    assertThat(notModified.failures()).isEmpty();
    LawImportEntity notModifiedRow =
        imports.findById(notModified.imports().getFirst()).orElseThrow();
    assertThat(notModifiedRow.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(notModifiedRow.getDatasetEtag()).isEqualTo("\"v1\"");
    assertThat(notModifiedRow.getArticlesUnchanged()).isZero();

    LawWatchReport same = watch("/law/CCQ-1991_FR-slice.xml", "\"v1\"");
    assertThat(same.unchanged()).isEqualTo(6);
    assertThat(imports.findById(same.imports().getFirst()).orElseThrow().getArticlesUnchanged())
        .isEqualTo(6);

    LawWatchReport amended = watch("/law/CCQ-1991_FR-slice-amended.xml", "\"v2\"");
    assertThat(amended.failures()).isEmpty();
    assertThat(imports.findById(amended.imports().getFirst()).orElseThrow().getArticlesChanged())
        .isEqualTo(1);
    assertThat(text("1942", LocalDate.of(2025, 1, 1))).contains("trois mois");
    assertThat(text("1942", LocalDate.of(2026, 10, 1))).contains("quatre mois");

    LawWatchReport reverted = watch("/law/CCQ-1991_FR-slice-reverted.xml", "\"v3\"");
    assertThat(reverted.failures()).isEmpty();
    assertThat(text("1942", LocalDate.of(2026, 10, 20))).contains("trois mois");
    assertThat(text("1942", LocalDate.of(2026, 10, 14))).contains("quatre mois");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from law_article_version where ref = '1942'", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from law_article_version where ref = '1942' and in_force_to is null",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            lookups
                .lookupStatute("QC", "CCQ-1991", "1943", "fr", LocalDate.of(2024, 1, 1))
                .outcome())
        .isEqualTo(StatuteLookup.Outcome.LAW_VERSION_UNKNOWN);

    LawWatchReport removed = watch("/law/CCQ-1991_FR-slice-1942-removed.xml", "\"v4\"");
    assertThat(removed.failures()).isEmpty();
    assertThat(
            lookups
                .lookupStatute("QC", "CCQ-1991", "1942", "fr", LocalDate.of(2026, 10, 23))
                .outcome())
        .isEqualTo(StatuteLookup.Outcome.LAW_VERSION_UNKNOWN);
    assertThat(text("1942", LocalDate.of(2026, 10, 22))).contains("trois mois");
  }

  @Test
  @DisplayName(
      "a manual import is a MANUAL import row with who and the file's hash, and the same rows")
  void manualImport() throws Exception {
    LawImportService importer = new LawImportService(store, settings, null, CLOCK);
    LawManualImportService manual =
        new LawManualImportService(
            List.of(
                new QuebecOpenDataSource(RestClient.create(), DATASET, new LawStatuteXmlParser())),
            settings,
            new LawXmlFileReader(new LawStatuteXmlParser()),
            importer);
    byte[] xml = resource("/law/CCQ-1991_FR-slice.xml");

    LawWatchReport report =
        manual.importFile(
            new ManualLawFile(
                "QC",
                "CCQ-1991",
                "fr",
                xml,
                "https://www.legisquebec.gouv.qc.ca/fr/pdf/cs/CCQ-1991.xml",
                "oussama",
                Sha256.hex(xml),
                LocalDate.of(2026, 10, 21)));

    LawImportEntity row = imports.findById(report.imports().getFirst()).orElseThrow();
    assertThat(row.getMode()).isEqualTo("MANUAL");
    assertThat(row.getImportedBy()).isEqualTo("oussama");
    assertThat(row.getDatasetSha256()).isEqualTo(Sha256.hex(xml));
    assertThat(row.getDatasetUrl())
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/pdf/cs/CCQ-1991.xml");
    assertThat(row.getArticlesNew()).isEqualTo(6);
    assertThat(text("1942", LocalDate.of(2026, 10, 23))).contains("trois mois");
    assertThat(
            lookups
                .lookupStatute("QC", "CCQ-1991", "1942", "fr", LocalDate.of(2026, 10, 23))
                .version()
                .importId())
        .isEqualTo(row.getId());
  }

  private String text(String ref, LocalDate asOf) {
    return lookups.lookupStatute("QC", "CCQ-1991", ref, "fr", asOf).version().text();
  }

  private static byte[] resource(String name) throws Exception {
    try (InputStream in = LawCorpusImportIT.class.getResourceAsStream(name)) {
      return Objects.requireNonNull(in, name).readAllBytes();
    }
  }

  private static byte[] zip(String name, byte[] content) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  /** C.c.Q. only, corpus on. */
  private static final class CcqSettings implements LawSettings {
    @Override
    public boolean corpusEnabled() {
      return true;
    }

    @Override
    public List<LawTarget> importInstruments() {
      return List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"));
    }

    @Override
    public int maxArticleChars() {
      return 20_000;
    }

    @Override
    public boolean researchEnabled() {
      return false;
    }

    @Override
    public int retrievalMaxArticles() {
      return 40;
    }

    @Override
    public int retrievalMaxAgeDays() {
      return 90;
    }

    @Override
    public com.krizaka.sovkora.core.domain.model.research.VerifyMode verifyMode() {
      return com.krizaka.sovkora.core.domain.model.research.VerifyMode.MARK;
    }

    @Override
    public int deriveMaxCredits() {
      return 0;
    }

    @Override
    public int deriveMaxRulesPerKind() {
      return 30;
    }

    @Override
    public int reviewMinGoldenCases() {
      return 3;
    }
  }
}
