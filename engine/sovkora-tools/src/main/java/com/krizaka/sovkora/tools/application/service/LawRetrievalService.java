package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.CitedArticle;
import com.krizaka.sovkora.tools.domain.model.law.CorpusDocument;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalRequest;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalResult;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage 2 of ADR-089: the law a kind of document is judged by, retrieved through grounded research
 * on the {@code law-corpus} source, and its cache.
 *
 * <ol>
 *   <li>Nothing runs unless {@code law.corpus.enabled}, {@code research.enabled} and the {@code
 *       law-corpus} source are all on (every one is off as seeded).
 *   <li>The provider reads the articles the kind targets at {@code asOf}, without network. The
 *       newest {@code in_force_from} among them is the as-of bucket.
 *   <li>A {@code law_retrieval} row for (jurisdiction, kind, bucket) that is not stale, is younger
 *       than {@code law.retrieval.max-age-days} and cites exactly these article versions is
 *       returned: no model call, no charge.
 *   <li>Otherwise one grounded call, held against the run budget as a metered {@code CALL}, asks
 *       the model for claims; the ADR-087 verifier checks each quote against the article text, and
 *       the verified claims are cached.
 * </ol>
 *
 * A claim that stays {@code TO_VALIDATE} is kept as such and is not used by the derivation stage.
 */
public class LawRetrievalService {

  private final LawSettings settings;
  private final ResearchStore research;
  private final LawCorpusResearchService provider;
  private final LawRetrievalCache cache;
  private final GroundedClaimsModel model;
  private final CitationVerifier verifier;
  private final ResearchMeter meter;
  private final Clock clock;

  /**
   * Wires the service.
   *
   * @param settings the {@code law.*} and {@code research.*} settings
   * @param research the research sources and documents
   * @param provider the {@code law-corpus} provider
   * @param cache {@code law_retrieval}
   * @param model the model port of the grounded call
   * @param verifier the ADR-087 citation verifier
   * @param meter the metering of a grounded call
   * @param clock the clock (injected)
   */
  public LawRetrievalService(
      LawSettings settings,
      ResearchStore research,
      LawCorpusResearchService provider,
      LawRetrievalCache cache,
      GroundedClaimsModel model,
      CitationVerifier verifier,
      ResearchMeter meter,
      Clock clock) {
    this.settings = Objects.requireNonNull(settings, "LawSettings");
    this.research = Objects.requireNonNull(research, "ResearchStore");
    this.provider = Objects.requireNonNull(provider, "LawCorpusResearchService");
    this.cache = Objects.requireNonNull(cache, "LawRetrievalCache");
    this.model = Objects.requireNonNull(model, "GroundedClaimsModel");
    this.verifier = Objects.requireNonNull(verifier, "CitationVerifier");
    this.meter = Objects.requireNonNull(meter, "ResearchMeter");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Retrieves the law for a kind of document.
   *
   * @param request the kind, the question, the targeted articles and the day
   * @return the articles used and the claims on them, from the cache or from one grounded call
   * @throws IllegalArgumentException when the language is not {@code fr} or {@code en}
   */
  public LawRetrievalResult retrieve(LawRetrievalRequest request) {
    String language = request.language().trim().toLowerCase(java.util.Locale.ROOT);
    if (!"fr".equals(language) && !"en".equals(language)) {
      throw new IllegalArgumentException("language must be fr or en");
    }
    Optional<ResearchSource> source = enabledSource();
    if (source.isEmpty()) {
      return LawRetrievalResult.empty(Outcome.DISABLED, List.of());
    }
    LocalDate day = request.asOf();
    String jurisdiction = request.jurisdiction().trim().toUpperCase(java.util.Locale.ROOT);
    LawCorpusResearchService.Read read =
        provider.read(
            jurisdiction, request.targets(), language, day, settings.retrievalMaxArticles());
    if (read.documents().isEmpty()) {
      return LawRetrievalResult.empty(Outcome.NO_ARTICLES, read.unknown());
    }
    List<CitedArticle> cited = read.documents().stream().map(CorpusDocument::cited).toList();
    LocalDate bucket =
        read.documents().stream()
            .map(CorpusDocument::inForceFrom)
            .max(Comparator.naturalOrder())
            .orElseThrow();

    Optional<StoredRetrieval> hit =
        cache
            .find(jurisdiction, request.kindId(), language, bucket)
            .filter(row -> reusable(row, cited));
    if (hit.isPresent()) {
      return new LawRetrievalResult(
          Outcome.CACHED, hit.get().articles(), hit.get().claims(), read.unknown());
    }
    return compute(request, source.get(), language, jurisdiction, bucket, read, cited);
  }

  private Optional<ResearchSource> enabledSource() {
    if (!settings.corpusEnabled() || !settings.researchEnabled()) {
      return Optional.empty();
    }
    return research
        .source(LawCorpusResearchService.SOURCE_KEY)
        .filter(ResearchSource::enabled)
        .filter(s -> LawCorpusResearchService.PROVIDER.equals(s.provider()));
  }

  /** Fresh, not stale, and citing exactly the versions the corpus holds for the day. */
  private boolean reusable(StoredRetrieval row, List<CitedArticle> cited) {
    Instant oldest = clock.instant().minus(Duration.ofDays(settings.retrievalMaxAgeDays()));
    return !row.stale()
        && !row.createdAt().isBefore(oldest)
        && new HashSet<>(row.articles()).equals(new HashSet<>(cited));
  }

  private LawRetrievalResult compute(
      LawRetrievalRequest request,
      ResearchSource source,
      String language,
      String jurisdiction,
      LocalDate bucket,
      LawCorpusResearchService.Read read,
      List<CitedArticle> cited) {
    ResearchMeter.Hold hold;
    try {
      hold = meter.hold(request.runId().toString(), request.actorId(), source.provider());
    } catch (ResearchMeter.BudgetExceededException refused) {
      return LawRetrievalResult.empty(Outcome.BUDGET_EXHAUSTED, read.unknown());
    }
    List<Claim> claims;
    try {
      List<ReadDocument> readByThisAnswer = provider.record(source, read.documents());
      GroundedAnswer answer = model.ask(request.question(), language, passages(read.documents()));
      claims = verifier.verify(answer, settings.verifyMode(), readByThisAnswer).claims();
    } catch (RuntimeException failure) {
      release(hold, failure);
      throw failure;
    }
    hold.settle();
    cache.save(
        new StoredRetrieval(
            jurisdiction,
            request.kindId(),
            language,
            bucket,
            cited,
            claims,
            request.runId(),
            clock.instant(),
            false));
    return new LawRetrievalResult(Outcome.RETRIEVED, cited, claims, read.unknown());
  }

  /** A failed release never hides the failure that caused it. */
  private static void release(ResearchMeter.Hold hold, RuntimeException failure) {
    try {
      hold.release("the grounded call failed");
    } catch (RuntimeException releaseFailure) {
      failure.addSuppressed(releaseFailure);
    }
  }

  private static List<GroundedClaimsModel.Passage> passages(List<CorpusDocument> documents) {
    return documents.stream()
        .map(
            d ->
                new GroundedClaimsModel.Passage(
                    d.officialUrl(), d.contentHash(), d.title(), d.text()))
        .toList();
  }
}
