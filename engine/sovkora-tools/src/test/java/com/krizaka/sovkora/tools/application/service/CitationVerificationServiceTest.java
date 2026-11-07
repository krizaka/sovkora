package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.krizaka.sovkora.core.domain.model.research.*;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.domain.model.research.FetchAudit;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deterministic verifier on recorded documents, in French and English (ADR-087 §3). The two
 * documents are test texts written for this suite, with no legal force.
 */
class CitationVerificationServiceTest {

  private static final String FR_URL = "https://example.org/reglement-fictif";
  private static final String EN_URL = "https://example.org/test-regulation";
  private static final String FR_HASH = "f".repeat(64);
  private static final String EN_HASH = "e".repeat(64);
  private static final UUID SITE = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  /** What this answer read: both documents, through the source « site ». */
  private static final List<ReadDocument> READ =
      List.of(new ReadDocument("site", FR_URL, FR_HASH), new ReadDocument("site", EN_URL, EN_HASH));

  private final Map<String, FetchedDocument> documents = new HashMap<>();
  private CitationVerificationService verifier;

  private static String golden(String name) {
    try (InputStream in =
        CitationVerificationServiceTest.class.getResourceAsStream("/research/golden/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void record(String url, String hash, String text) {
    Instant at = Instant.parse("2026-10-23T12:00:00Z");
    documents.put(
        SITE + "|" + url + "#" + hash,
        new FetchedDocument(
            url, hash, at, 200, "text/plain", text.length(), "", text, at.plusSeconds(60)));
  }

  private static ResearchSource declaredSource(UUID id, String key) {
    return new ResearchSource(
        id, key, "FETCH", "http", List.of("example.org/**"), List.of(), false, 60, 10, true);
  }

  @BeforeEach
  void setUp() {
    record(FR_URL, FR_HASH, golden("fr-reglement-fictif.txt"));
    record(EN_URL, EN_HASH, golden("en-test-regulation.txt"));
    ResearchStore store =
        new ResearchStore() {
          @Override
          public Optional<ResearchSource> source(String sourceKey) {
            return switch (sourceKey) {
              case "site" -> Optional.of(declaredSource(SITE, "site"));
              case "other" -> Optional.of(declaredSource(OTHER, "other"));
              default -> Optional.empty();
            };
          }

          @Override
          public Optional<FetchedDocument> freshDocument(
              UUID sourceId, String url, Instant notBefore) {
            return Optional.empty();
          }

          @Override
          public Optional<FetchedDocument> document(UUID sourceId, String url, String contentHash) {
            return Optional.ofNullable(documents.get(sourceId + "|" + url + "#" + contentHash));
          }

          @Override
          public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {}

          @Override
          public void audit(FetchAudit audit) {}
        };
    RuntimeConfigProvider config =
        new RuntimeConfigProvider() {
          @Override
          public boolean getBoolean(String key, boolean defaultValue) {
            return defaultValue;
          }

          @Override
          public int getInt(String key, int defaultValue) {
            return "research.quote.max-chars".equals(key) ? 120 : defaultValue;
          }

          @Override
          public String getString(String key, String defaultValue) {
            return defaultValue;
          }
        };
    verifier = new CitationVerificationService(store, config);
  }

  private static Claim claim(String text, String url, String hash, String quote) {
    return new Claim(
        text, null, null, List.of(new Citation(url, quote, hash, "2026-10-23T12:00:00Z")));
  }

  private GroundedAnswer verify(VerifyMode mode, String prose, Claim... claims) {
    return verifier.verify(new GroundedAnswer(prose, List.of(claims), List.of()), mode, READ);
  }

  @Test
  @DisplayName("FR golden: plain typing finds the passage despite « », ’, U+202F, U+00A0, œ")
  void frenchQuoteIsFound() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "L'article 12 interdit une somme autre que le loyer.",
                FR_URL,
                FR_HASH,
                "ne peut exiger \"une somme d'argent\" autre que le loyer ; il répond de l'oeuvre : c'est la règle"));
    assertThat(out.claims())
        .singleElement()
        .extracting(Claim::status)
        .isEqualTo(ClaimStatus.VERIFIED);
  }

  @Test
  @DisplayName("FR golden: an NFD quote is the same passage")
  void frenchNfdQuoteIsFound() {
    String nfd =
        Normalizer.normalize("Le délai de résiliation est de trente jours", Normalizer.Form.NFD);
    GroundedAnswer out = verify(VerifyMode.MARK, "", claim("Trente jours.", FR_URL, FR_HASH, nfd));
    assertThat(out.claims().getFirst().status()).isEqualTo(ClaimStatus.VERIFIED);
  }

  @Test
  @DisplayName("FR golden: one word changed is not the passage")
  void frenchChangedWordIsNotFound() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "Quatorze jours.",
                FR_URL,
                FR_HASH,
                "Le délai de résiliation est de quatorze jours"));
    assertThat(out.claims().getFirst().status()).isEqualTo(ClaimStatus.TO_VALIDATE);
    assertThat(out.claims().getFirst().reason()).isEqualTo(ClaimReason.QUOTE_NOT_FOUND);
  }

  @Test
  @DisplayName("FR golden: a genuine quote under a wrong number stays TO_VALIDATE (UNSUPPORTED)")
  void aGenuineQuoteUnderAWrongNumberIsUnsupported() {
    String genuine = "Le délai de résiliation est de trente jours";
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "Le délai de résiliation est de 14 jours (article 13).", FR_URL, FR_HASH, genuine),
            claim("Le délai est de trente jours (article 13).", FR_URL, FR_HASH, genuine),
            claim("L'article 99 fixe le délai à trente jours.", FR_URL, FR_HASH, genuine));
    assertThat(out.claims())
        .extracting(Claim::status, Claim::reason)
        .containsExactly(
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED),
            tuple(ClaimStatus.VERIFIED, null),
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED));
  }

  @Test
  @DisplayName(
      "EN golden: curly quotes, the em dash and the ellipsis match; a changed word does not;"
          + " a section number is supported by its heading, a wrong one is not")
  void englishGolden() {
    String genuine = "takes effect sixty days after it is received";
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "Only the first payment is due.",
                EN_URL,
                EN_HASH,
                "The tenant's \"first payment\" - and only that... is due at signing"),
            claim("Ninety days.", EN_URL, EN_HASH, "takes effect ninety days after it is received"),
            claim(
                "Section 5: a notice takes effect sixty days after receipt.",
                EN_URL,
                EN_HASH,
                genuine),
            claim(
                "Section 4: a notice takes effect sixty days after receipt.",
                EN_URL,
                EN_HASH,
                genuine));
    assertThat(out.claims())
        .extracting(Claim::status, Claim::reason)
        .containsExactly(
            tuple(ClaimStatus.VERIFIED, null),
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.QUOTE_NOT_FOUND),
            tuple(ClaimStatus.VERIFIED, null),
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED));
  }

  @Test
  @DisplayName("no citation, an unknown document, a quote too long: never verified")
  void notVerified() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            new Claim("Said from memory.", null, null, List.of()),
            claim("Unknown document.", EN_URL, "0".repeat(64), "first payment"),
            claim("Too long.", EN_URL, EN_HASH, "x".repeat(121)));
    assertThat(out.claims())
        .extracting(Claim::reason)
        .containsExactly(
            ClaimReason.NO_SOURCE, ClaimReason.URL_UNRESOLVED, ClaimReason.QUOTE_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "a document this answer did not read never verifies, nor one read through another source")
  void aDocumentNotReadByThisAnswerNeverVerifies() {
    Claim fr = claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite");
    GroundedAnswer answer = new GroundedAnswer("", List.of(fr), List.of());

    assertThat(verifier.verify(answer, VerifyMode.MARK, List.of()).claims().getFirst().reason())
        .as("nothing was read for this answer")
        .isEqualTo(ClaimReason.URL_UNRESOLVED);
    List<ReadDocument> onlyEnglish = List.of(new ReadDocument("site", EN_URL, EN_HASH));
    assertThat(verifier.verify(answer, VerifyMode.MARK, onlyEnglish).claims().getFirst().reason())
        .as("the French document is in the store, but this answer did not read it")
        .isEqualTo(ClaimReason.URL_UNRESOLVED);
    List<ReadDocument> throughOther = List.of(new ReadDocument("other", FR_URL, FR_HASH));
    assertThat(verifier.verify(answer, VerifyMode.MARK, throughOther).claims().getFirst().reason())
        .as("the store is asked through the document's own source, which never read it")
        .isEqualTo(ClaimReason.URL_UNRESOLVED);
    assertThat(verifier.verify(answer, VerifyMode.MARK, READ).claims().getFirst().status())
        .isEqualTo(ClaimStatus.VERIFIED);
  }

  @Test
  @DisplayName("invented references in prose become TO_VALIDATE lines; cited ones do not")
  void unlistedReferences() {
    String prose =
        "See https://www.legisquebec.gouv.qc.ca/invented and the repository krizaka/legal-rules;"
            + " article 12 applies, and art. 99 too.";
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            prose,
            claim(
                "L'article 12 interdit une somme autre que le loyer.",
                FR_URL,
                FR_HASH,
                "une somme d’argent"));
    assertThat(out.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .contains("https://www.legisquebec.gouv.qc.ca/invented", "krizaka/legal-rules", "art. 99")
        .doesNotContain("article 12");
    assertThat(out.answer())
        .contains("[TO_VALIDATE] krizaka/legal-rules", "[TO_VALIDATE] art. 99")
        .doesNotContain("See https://");
  }

  @Test
  @DisplayName("the served answer is rendered from the claims, never from the model's prose")
  void theAnswerIsRenderedFromTheClaims() {
    String prose = "Quatorze jours partout, et trente jours.";
    GroundedAnswer marked =
        verify(
            VerifyMode.MARK,
            prose,
            claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite"),
            claim("Quatorze jours.", FR_URL, FR_HASH, "quatorze jours"));
    assertThat(marked.answer()).isEqualTo("Trente jours.\n[TO_VALIDATE] Quatorze jours.");
    assertThat(marked.answer()).doesNotContain("partout");

    GroundedAnswer empty = verify(VerifyMode.MARK, prose);
    assertThat(empty.answer()).isEmpty();
    assertThat(empty.claims()).isEmpty();
  }

  @Test
  @DisplayName("STRICT drops what is not verified, and the answer keeps only verified claims")
  void strictDrops() {
    GroundedAnswer out =
        verify(
            VerifyMode.STRICT,
            "Trente jours, et quatorze ailleurs.",
            claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite"),
            claim("Quatorze jours.", FR_URL, FR_HASH, "quatorze jours"));
    assertThat(out.claims()).extracting(Claim::text).containsExactly("Trente jours.");
    assertThat(out.answer()).isEqualTo("Trente jours.");
  }

  @Test
  @DisplayName("the model's own status is never trusted")
  void statusComesFromTheVerifier() {
    Claim lying =
        new Claim(
            "Quatorze jours.",
            ClaimStatus.VERIFIED,
            null,
            List.of(new Citation(FR_URL, "quatorze jours", FR_HASH, "")));
    assertThat(verify(VerifyMode.MARK, "", lying).claims().getFirst().status())
        .isEqualTo(ClaimStatus.TO_VALIDATE);
  }

  @Test
  @DisplayName("the heading of a quote's section is its paragraph's label, else the heading above")
  void sectionHeading() {
    String text =
        "Title\n\nChapter 2\n\nSome intro.\n\nArticle 7. The rule is this.\n\nMore text here.";
    assertThat(CitationVerificationService.sectionHeading(text, "the rule is this"))
        .isEqualTo("Article 7");
    assertThat(CitationVerificationService.sectionHeading(text, "more text here"))
        .isEqualTo("Chapter 2");
    assertThat(CitationVerificationService.sectionHeading(text, "absent")).isEmpty();
  }

  // --- The review of #70 (threads 2 to 8, closes #88): one planted regression each
  // -----------------

  private static final String EXTRA_URL = "https://example.org/delais";
  private static final String EXTRA_HASH = "d".repeat(64);
  private static final List<ReadDocument> READ_EXTRA =
      List.of(
          new ReadDocument("site", FR_URL, FR_HASH),
          new ReadDocument("site", EN_URL, EN_HASH),
          new ReadDocument("site", EXTRA_URL, EXTRA_HASH));

  private GroundedAnswer verifyExtra(VerifyMode mode, String prose, Claim... claims) {
    record(
        EXTRA_URL,
        EXTRA_HASH,
        """
        Article 12. Le délai de préavis est de 140 jours en 2024.

        Article 1931. Le préavis\
         de visite est de 24 heures.""");
    return verifier.verify(new GroundedAnswer(prose, List.of(claims), List.of()), mode, READ_EXTRA);
  }

  @Test
  @DisplayName("#88: a number is a whole token — 14 is not 140, 24 is not 2024, 1 is not 1931")
  void numbersAreWholeTokens() {
    GroundedAnswer out =
        verifyExtra(
            VerifyMode.MARK,
            "",
            claim(
                "Le délai est de 14 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours"),
            claim(
                "Le délai est de 140 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours"),
            claim("Le préavis est de 24 mois.", EXTRA_URL, EXTRA_HASH, "en 2024"),
            claim(
                "Le préavis est de 24 heures (article 1931).",
                EXTRA_URL,
                EXTRA_HASH,
                "Le préavis de visite est de 24 heures"),
            claim(
                "Le préavis est de 24 heures (article 193).",
                EXTRA_URL,
                EXTRA_HASH,
                "Le préavis de visite est de 24 heures"));
    assertThat(out.claims())
        .extracting(Claim::status, Claim::reason)
        .containsExactly(
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED),
            tuple(ClaimStatus.VERIFIED, null),
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED),
            tuple(ClaimStatus.VERIFIED, null),
            tuple(ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED));
  }

  @Test
  @DisplayName(
      "#88: « Article 1 » is not supported by the heading « Article 12 », « art. » = « article »")
  void referencesAreWholeAndSpellingsEquivalent() {
    GroundedAnswer out =
        verifyExtra(
            VerifyMode.MARK,
            "",
            claim(
                "L'article 1 fixe le délai de préavis à 140 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours"),
            claim(
                "L'art. 12 fixe le délai de préavis à 140 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours"),
            claim(
                "Le dépôt krizaka/sovkora-pack fixe 140 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours"));
    assertThat(out.claims())
        .extracting(Claim::status)
        .containsExactly(ClaimStatus.TO_VALIDATE, ClaimStatus.VERIFIED, ClaimStatus.TO_VALIDATE);
  }

  @Test
  @DisplayName(
      "a verified claim keeps only the citations that passed: an invented one is dropped and its URL in prose is unlisted")
  void aVerifiedClaimKeepsOnlyItsPassingCitations() {
    Claim mixed =
        new Claim(
            "Le délai de résiliation est de trente jours.",
            null,
            null,
            List.of(
                new Citation("https://example.org/invented", "trente jours", FR_HASH, ""),
                new Citation(FR_URL, "Le délai de résiliation est de trente jours", FR_HASH, ""),
                new Citation(FR_URL, "quarante jours inventés", FR_HASH, "")));

    GroundedAnswer out =
        verify(VerifyMode.MARK, "Voir https://example.org/invented pour le détail.", mixed);

    Claim verified = out.claims().getFirst();
    assertThat(verified.status()).isEqualTo(ClaimStatus.VERIFIED);
    assertThat(verified.citations()).extracting(Citation::url).containsExactly(FR_URL);
    assertThat(verified.citations())
        .extracting(Citation::quote)
        .containsExactly("Le délai de résiliation est de trente jours");
    assertThat(out.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .containsExactly("https://example.org/invented");
  }

  @Test
  @DisplayName(
      "digits alone are not a statute reference: « 12 jours » does not cover « art. 12 », nor « article 12 » « art. 1 »")
  void digitsAloneDoNotCoverAStatute() {
    GroundedAnswer quantity =
        verifyExtra(
            VerifyMode.MARK,
            "Voir aussi art. 12.",
            claim(
                "Le préavis est de 12 jours.",
                EXTRA_URL,
                EXTRA_HASH,
                "Le délai de préavis est de 140 jours en 2024"));
    // the claim itself is unsupported, so use a document that really says 12:
    record("https://example.org/douze", "c".repeat(64), "Le préavis est de 12 jours.");
    List<ReadDocument> read =
        List.of(new ReadDocument("site", "https://example.org/douze", "c".repeat(64)));
    GroundedAnswer verifiedQuantity =
        verifier.verify(
            new GroundedAnswer(
                "Voir aussi art. 12.",
                List.of(
                    claim(
                        "Le préavis est de 12 jours.",
                        "https://example.org/douze",
                        "c".repeat(64),
                        "Le préavis est de 12 jours")),
                List.of()),
            VerifyMode.MARK,
            read);
    assertThat(quantity.claims()).hasSize(2);
    assertThat(verifiedQuantity.claims().getFirst().status()).isEqualTo(ClaimStatus.VERIFIED);
    assertThat(verifiedQuantity.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .containsExactly("art. 12");

    GroundedAnswer prefix =
        verify(
            VerifyMode.MARK,
            "L'art. 1 aussi, et l'article 12.",
            claim(
                "L'article 12 interdit une somme autre que le loyer.",
                FR_URL,
                FR_HASH,
                "une somme d’argent"));
    assertThat(prefix.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .containsExactly("art. 1");
  }

  @Test
  @DisplayName(
      "a URL is covered only by the complete cited URL, case-sensitive: a shared prefix is not coverage")
  void urlCoverageIsTheCompleteUrl() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "Voir https://example.org/reglement-fictif-invented, https://example.org/Reglement-Fictif,"
                + " https://example.org et https://example.org/reglement-fictif.",
            claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite"));
    assertThat(out.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .containsExactly(
            "https://example.org/reglement-fictif-invented",
            "https://example.org/Reglement-Fictif",
            "https://example.org");
  }

  @Test
  @DisplayName("STRICT appends no unlisted reference: claims[] keeps verified claims only")
  void strictDropsUnlistedReferencesFromTheClaims() {
    GroundedAnswer none = verify(VerifyMode.STRICT, "See https://invented.example/x and art. 99.");
    assertThat(none.claims()).isEmpty();
    assertThat(none.answer()).isEmpty();

    GroundedAnswer some =
        verify(
            VerifyMode.STRICT,
            "See https://invented.example/x.",
            claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite"));
    assertThat(some.claims()).extracting(Claim::status).containsOnly(ClaimStatus.VERIFIED);
  }
}
