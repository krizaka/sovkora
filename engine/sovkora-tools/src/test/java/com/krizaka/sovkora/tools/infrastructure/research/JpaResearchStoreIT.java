package com.krizaka.sovkora.tools.infrastructure.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.domain.model.research.FetchAudit;
import com.krizaka.sovkora.tools.domain.model.research.FetchRefusal;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchSourceEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.ResearchFetchRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.ResearchSourceRepository;
import java.time.Instant;
import java.util.UUID;
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

/**
 * The research tables on PostgreSQL: arrays, the source + URL + hash key, the final URL, freshness
 * as the requesting source sees it, and the audit.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaResearchStore.class)
class JpaResearchStoreIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  @Autowired private JpaResearchStore store;
  @Autowired private ResearchSourceRepository sources;
  @Autowired private ResearchFetchRepository fetches;

  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final UUID SOURCE = UUID.randomUUID();
  private static final UUID OTHER_SOURCE = UUID.randomUUID();

  @Test
  @DisplayName("a source's patterns round-trip as PostgreSQL arrays")
  void sourcePatterns() {
    ResearchSourceEntity row = new ResearchSourceEntity();
    row.setId(UUID.randomUUID());
    row.setSourceKey("justice-laws");
    row.setKind("OFFICIAL");
    row.setProvider("justice-laws");
    row.setAllowPatterns(new String[] {"laws-lois.justice.gc.ca/**"});
    row.setDenyPatterns(new String[] {"laws-lois.justice.gc.ca/private/**"});
    row.setFreshnessSeconds(604_800);
    row.setRatePerMinute(30);
    row.setEnabled(true);
    sources.saveAndFlush(row);

    var source = store.source("justice-laws").orElseThrow();
    assertThat(source.allowPatterns()).containsExactly("laws-lois.justice.gc.ca/**");
    assertThat(source.denyPatterns()).containsExactly("laws-lois.justice.gc.ca/private/**");
    assertThat(store.source("missing")).isEmpty();
  }

  @Test
  @DisplayName(
      "a redirected document is a hit under the asked URL and the final one, reports its final URL,"
          + " only through its source, only while the requesting source finds it fresh")
  void freshnessFinalUrlAndSourceScope() {
    String hash = "a".repeat(64);
    FetchedDocument doc =
        new FetchedDocument(
            "https://final.test/b",
            hash,
            NOW,
            200,
            "text/html",
            12,
            "t",
            "text",
            NOW.plusSeconds(60));
    store.saveDocument(doc, "https://asked.test/a", SOURCE);

    assertThat(store.freshDocument(SOURCE, "https://asked.test/a", NOW.minusSeconds(30)))
        .map(FetchedDocument::url)
        .contains("https://final.test/b");
    assertThat(store.freshDocument(SOURCE, "https://final.test/b", NOW.minusSeconds(30)))
        .map(FetchedDocument::contentHash)
        .contains(hash);
    // The freshness is the requesting source's own, applied to when the document was read.
    assertThat(store.freshDocument(SOURCE, "https://final.test/b", NOW.plusSeconds(1))).isEmpty();
    // Another source never gets this document, however fresh.
    assertThat(store.freshDocument(OTHER_SOURCE, "https://asked.test/a", NOW.minusSeconds(30)))
        .isEmpty();
  }

  @Test
  @DisplayName("an audit line keeps the refusal reason")
  void audit() {
    store.audit(
        new FetchAudit(
            NOW,
            "actor",
            null,
            null,
            null,
            "FETCH",
            "site.test",
            "b".repeat(64),
            null,
            null,
            3,
            false,
            FetchRefusal.SSRF));
    assertThat(fetches.findAll())
        .anyMatch(
            row -> "SSRF".equals(row.getRefusedReason()) && "site.test".equals(row.getHost()));
  }
}
