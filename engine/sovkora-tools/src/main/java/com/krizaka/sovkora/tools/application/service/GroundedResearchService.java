package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimReason;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.GroundedSource;
import com.krizaka.sovkora.core.domain.model.research.QuoteNormalizer;
import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.GroundedResearchSettings;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.CorpusDocument;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchRequest;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The capability {@code orazaka.core.research.grounded} (handler {@code research.grounded}, ADR-087
 * §1 and §3, ADR-089 §6): an answer made only of what was read, each claim cited, or a refusal.
 *
 * <ol>
 *   <li>Off unless {@code research.enabled}, {@code law.corpus.enabled} and the {@code law-corpus}
 *       source are on; a call with a document also needs {@code law.lecture.enabled}. A source
 *       other than {@code law-corpus} is refused, never guessed.
 *   <li>The articles are read from the dated corpus at {@code asOf}, without network. When none was
 *       in force, the model is not asked: the call is refused ({@code NO_SOURCES}) and says which
 *       references are unknown.
 *   <li>One call, held against the run budget as a metered {@code CALL} and, beside it, the chat
 *       tokens of the model (each settled once: the call as one {@code CALL}, the tokens as the
 *       provider reported them), asks the model; a lecture whose two reservations together pass
 *       {@code law.lecture.max-credits} is refused before the model is asked ({@code
 *       CREDIT_CAP_REACHED}). The deterministic verifier then checks every claim against the stored
 *       article. The answer that leaves is rendered from the claims, never taken from the model,
 *       and its sources are the documents read, not the ones the model names.
 *   <li>A lecture adds the user's document <b>as data</b>, never stored: a claim stays {@code
 *       TO_VALIDATE} unless one of its quotes is found in the article <b>and</b> one in the
 *       document.
 * </ol>
 */
public class GroundedResearchService {

  private static final String DOCUMENT_URN = "urn:sovkora:document:";
  private static final String MARK_PREFIX = "[TO_VALIDATE] ";

  private final GroundedResearchSettings settings;
  private final ResearchStore research;
  private final LawCorpusResearchService provider;
  private final GroundedClaimsModel model;
  private final CitationVerifier verifier;
  private final ResearchMeter meter;
  private final ChatTokenMeter tokenMeter;

  /**
   * Wires the service.
   *
   * @param settings the settings, read at each call
   * @param research the research sources and documents
   * @param provider the {@code law-corpus} provider
   * @param model the model port of the grounded call
   * @param verifier the ADR-087 citation verifier
   * @param meter the metering of a grounded call
   * @param tokenMeter the metering of the chat tokens of a grounded call
   */
  public GroundedResearchService(
      GroundedResearchSettings settings,
      ResearchStore research,
      LawCorpusResearchService provider,
      GroundedClaimsModel model,
      CitationVerifier verifier,
      ResearchMeter meter,
      ChatTokenMeter tokenMeter) {
    this.settings = Objects.requireNonNull(settings, "GroundedResearchSettings");
    this.research = Objects.requireNonNull(research, "ResearchStore");
    this.provider = Objects.requireNonNull(provider, "LawCorpusResearchService");
    this.model = Objects.requireNonNull(model, "GroundedClaimsModel");
    this.verifier = Objects.requireNonNull(verifier, "CitationVerifier");
    this.meter = Objects.requireNonNull(meter, "ResearchMeter");
    this.tokenMeter = Objects.requireNonNull(tokenMeter, "ChatTokenMeter");
  }

  /**
   * Answers a question from the sources, or refuses.
   *
   * @param request the call
   * @return the verified answer, or a refusal that says why
   * @throws IllegalArgumentException when {@code law-corpus} is asked without a jurisdiction, an
   *     {@code asOf} day or targets
   */
  public GroundedResearchResult research(GroundedResearchRequest request) {
    if (!List.of(LawCorpusResearchService.SOURCE_KEY).equals(request.sources())) {
      return GroundedResearchResult.refused(Outcome.UNSUPPORTED_SOURCE, List.of());
    }
    requireCorpusInputs(request);
    Optional<ResearchSource> source = enabledSource();
    if (source.isEmpty()) {
      return GroundedResearchResult.refused(Outcome.DISABLED, List.of());
    }
    if (request.isLecture() && !settings.lectureEnabled()) {
      return GroundedResearchResult.refused(Outcome.LECTURE_DISABLED, List.of());
    }
    if (request.isLecture() && settings.lectureMaxCredits() <= 0) {
      return GroundedResearchResult.refused(Outcome.CREDIT_CAP_REACHED, List.of());
    }
    LawCorpusResearchService.Read read =
        provider.read(
            request.jurisdiction(),
            request.targets(),
            request.language(),
            request.asOf(),
            settings.maxArticles());
    if (read.documents().isEmpty()) {
      return GroundedResearchResult.refused(Outcome.NO_SOURCES, read.unknown());
    }
    return ask(request, source.get(), read);
  }

  private static void requireCorpusInputs(GroundedResearchRequest request) {
    if (request.jurisdiction() == null || request.jurisdiction().isBlank()) {
      throw new IllegalArgumentException("jurisdiction is required for law-corpus");
    }
    if (request.asOf() == null) {
      throw new IllegalArgumentException("asOf is required: the day the law is read at");
    }
    if (request.targets().isEmpty()) {
      throw new IllegalArgumentException("targets name at least one instrument for law-corpus");
    }
  }

  private Optional<ResearchSource> enabledSource() {
    if (!settings.researchEnabled() || !settings.corpusEnabled()) {
      return Optional.empty();
    }
    return research
        .source(LawCorpusResearchService.SOURCE_KEY)
        .filter(ResearchSource::enabled)
        .filter(s -> LawCorpusResearchService.PROVIDER.equals(s.provider()));
  }

  private GroundedResearchResult ask(
      GroundedResearchRequest request, ResearchSource source, LawCorpusResearchService.Read read) {
    Holds holds;
    try {
      holds = hold(request, source);
    } catch (ResearchMeter.BudgetExceededException refused) {
      return GroundedResearchResult.refused(Outcome.BUDGET_EXHAUSTED, read.unknown());
    }
    if (request.isLecture() && holds.estimatedCredits() > settings.lectureMaxCredits()) {
      holds.release(new IllegalStateException("lecture cap reached"));
      return GroundedResearchResult.refused(Outcome.CREDIT_CAP_REACHED, read.unknown());
    }
    Answered answered;
    try {
      List<ReadDocument> readByThisAnswer = provider.record(source, read.documents());
      answered = answer(request, read.documents(), readByThisAnswer);
    } catch (RuntimeException failure) {
      holds.release(failure);
      throw failure;
    }
    holds.settle(answered.usage());
    return new GroundedResearchResult(
        Outcome.ANSWERED, answered.verified(), sourcesRead(read.documents()), read.unknown());
  }

  /**
   * Holds the source's call, then the model's tokens; when the second fails, the first is freed.
   */
  private Holds hold(GroundedResearchRequest request, ResearchSource source) {
    String run = request.runId().toString();
    ResearchMeter.Hold call = meter.hold(run, request.actorId(), source.provider());
    try {
      return new Holds(call, tokenMeter.hold(run, request.actorId()));
    } catch (RuntimeException refused) {
      release(call, refused);
      throw refused;
    }
  }

  /** The two reservations of one grounded call, closed together and once each. */
  private record Holds(ResearchMeter.Hold call, ChatTokenMeter.Hold tokens) {

    long estimatedCredits() {
      return call.estimatedCredits() + tokens.estimatedCredits();
    }

    void settle(TokenUsage usage) {
      call.settle();
      tokens.settle(usage);
    }

    /** A failed release never hides the failure that caused it. */
    void release(RuntimeException failure) {
      GroundedResearchService.release(call, failure);
      GroundedResearchService.release(tokens, failure);
    }
  }

  /** A verified answer and the tokens the model call consumed. */
  private record Answered(GroundedAnswer verified, TokenUsage usage) {}

  private Answered answer(
      GroundedResearchRequest request, List<CorpusDocument> documents, List<ReadDocument> read) {
    List<GroundedClaimsModel.Passage> passages = new ArrayList<>();
    for (CorpusDocument d : documents) {
      passages.add(
          new GroundedClaimsModel.Passage(d.officialUrl(), d.contentHash(), d.title(), d.text()));
    }
    if (!request.isLecture()) {
      GroundedClaimsModel.Reply reply =
          model.askMetered(request.question(), request.language(), passages);
      return new Answered(
          verifier.verify(reply.answer(), settings.verifyMode(), read), reply.usage());
    }
    String hash = Sha256.hex(request.document().getBytes(StandardCharsets.UTF_8));
    String url = DOCUMENT_URN + hash;
    passages.add(new GroundedClaimsModel.Passage(url, hash, "document", request.document()));
    GroundedClaimsModel.Reply reply =
        model.askMetered(request.question(), request.language(), passages);
    return new Answered(
        pairWithDocument(reply.answer(), new DocumentRef(url, hash, request.document()), read),
        reply.usage());
  }

  /** The user's document, held in memory for one call: never stored, never logged (ADR-064). */
  private record DocumentRef(String url, String hash, String text) {}

  /**
   * A lecture claim pairs an article with a passage of the document. The verifier sees only the
   * article side; the document side is checked here against the text in memory. Both must hold.
   */
  private GroundedAnswer pairWithDocument(
      GroundedAnswer proposed, DocumentRef document, List<ReadDocument> read) {
    List<Claim> articleSide = new ArrayList<>();
    List<List<Citation>> documentSide = new ArrayList<>();
    for (Claim claim : proposed.claims()) {
      List<Citation> own = new ArrayList<>();
      List<Citation> other = new ArrayList<>();
      for (Citation c : claim.citations()) {
        (document.url().equals(c.url()) ? own : other).add(c);
      }
      articleSide.add(new Claim(claim.text(), claim.status(), claim.reason(), other));
      documentSide.add(own);
    }
    // MARK keeps every claim in place so that the two sides line up; the mode is applied below.
    GroundedAnswer checked =
        verifier.verify(
            new GroundedAnswer(proposed.answer(), articleSide, proposed.sources()),
            VerifyMode.MARK,
            read);
    List<Claim> result = new ArrayList<>();
    for (int i = 0; i < checked.claims().size(); i++) {
      Claim claim = checked.claims().get(i);
      if (i >= documentSide.size()) {
        result.add(claim);
      } else {
        result.add(withDocument(claim, documentSide.get(i), document));
      }
    }
    return render(result, settings.verifyMode());
  }

  private Claim withDocument(Claim claim, List<Citation> cited, DocumentRef document) {
    if (claim.status() != ClaimStatus.VERIFIED) {
      return claim;
    }
    List<Citation> found = cited.stream().filter(c -> foundInDocument(c, document)).toList();
    if (found.isEmpty()) {
      return new Claim(claim.text(), ClaimStatus.TO_VALIDATE, ClaimReason.UNSUPPORTED, cited);
    }
    List<Citation> both = new ArrayList<>(claim.citations());
    both.addAll(found);
    return new Claim(claim.text(), ClaimStatus.VERIFIED, null, both);
  }

  private boolean foundInDocument(Citation citation, DocumentRef document) {
    return document.hash().equals(citation.contentHash())
        && citation.quote().length() <= settings.quoteMaxChars()
        && QuoteNormalizer.contains(document.text(), citation.quote());
  }

  private static GroundedAnswer render(List<Claim> claims, VerifyMode mode) {
    List<Claim> kept =
        mode == VerifyMode.STRICT
            ? claims.stream().filter(c -> c.status() == ClaimStatus.VERIFIED).toList()
            : claims;
    List<String> lines = new ArrayList<>();
    for (Claim claim : kept) {
      lines.add(claim.status() == ClaimStatus.VERIFIED ? claim.text() : MARK_PREFIX + claim.text());
    }
    return new GroundedAnswer(String.join("\n", lines), kept, List.of());
  }

  private static List<GroundedSource> sourcesRead(List<CorpusDocument> documents) {
    return documents.stream()
        .map(
            d ->
                new GroundedSource(d.officialUrl(), d.title(), LawCorpusResearchService.SOURCE_KEY))
        .toList();
  }

  /** A failed release never hides the failure that caused it. */
  private static void release(ResearchMeter.Hold hold, RuntimeException failure) {
    try {
      hold.release("the grounded call did not run");
    } catch (RuntimeException releaseFailure) {
      failure.addSuppressed(releaseFailure);
    }
  }

  private static void release(ChatTokenMeter.Hold hold, RuntimeException failure) {
    try {
      hold.release("the grounded call did not run");
    } catch (RuntimeException releaseFailure) {
      failure.addSuppressed(releaseFailure);
    }
  }
}
