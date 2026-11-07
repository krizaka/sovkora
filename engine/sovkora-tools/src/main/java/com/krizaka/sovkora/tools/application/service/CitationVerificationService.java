package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.research.*;
import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ReferenceScanner;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The deterministic verifier of ADR-087 §3. A claim is {@code VERIFIED} only when one of its
 * citations passes two checks, with no model involved:
 *
 * <ol>
 *   <li><b>Provenance.</b> The citation names a document <b>this answer read</b> — one the
 *       grounding stage recorded ({@link ReadDocument}) — and the store still holds those bytes,
 *       asked through that document's own source. The quote, at most {@code
 *       research.quote.max-chars}, appears in them once both are normalised ({@link
 *       QuoteNormalizer}: NFC, case, French typography).
 *   <li><b>Support.</b> Every number, date and reference the claim states (digits, URLs, {@code
 *       owner/repo} names, statute references) appears in the quote, in the cited URL, in the
 *       document's title, or in the heading of the section the quote is in — a genuine quote under
 *       a wrong number or statute stays {@code TO_VALIDATE} ({@code UNSUPPORTED}). The section
 *       heading is the quote's paragraph's own label (« Article 12 », « Section 5 »), else the
 *       nearest short, unpunctuated paragraph above it.
 * </ol>
 *
 * Any other claim is {@code TO_VALIDATE} with the furthest reason it reached ({@code NO_SOURCE},
 * {@code URL_UNRESOLVED}, {@code QUOTE_NOT_FOUND}, {@code UNSUPPORTED}), or dropped in {@code
 * STRICT}. Every URL, repository and statute stated in the model's prose that no verified claim
 * covers becomes a {@code TO_VALIDATE} line of its own ({@code UNLISTED_REFERENCE}). The served
 * {@code answer} is rendered from the claims — verified claims as they are, {@code [TO_VALIDATE]}
 * prefixes in {@code MARK}, verified claims only in {@code STRICT}, empty with no claims — never
 * the model's prose, which is only scanned. A claim is never rewritten: only its status moves.
 */
@Service
public class CitationVerificationService implements CitationVerifier {

  /**
   * A number, a date or a numbered reference as a claim writes it: 14, 1904, 2026-10-23, P-40.1.
   */
  private static final Pattern NUMBERS = Pattern.compile("\\d++(?:[.,:/\\-]\\d++)*+");

  /** The label a section's paragraph starts with: « Article 12. », « Section 5 », « § 3 ». */
  private static final Pattern SECTION_LABEL =
      Pattern.compile(
          "(?iu)^(?:art(?:icle)?s?\\.?|section|§|chapitre|chapter|partie|part|titre|title)"
              + "\\s*+\\d++[\\w-]*+(?:\\.\\w[\\w-]*+)*+");

  private static final Pattern PARAGRAPHS = Pattern.compile("\\n[ \\t]*\\n");

  /** A heading is short and does not end like a sentence. */
  private static final int HEADING_MAX_CHARS = 120;

  private static final String MARK_PREFIX = "[TO_VALIDATE] ";

  private final ResearchStore store;
  private final RuntimeConfigProvider config;

  /**
   * Wires the verifier.
   *
   * @param store the documents research read, by source
   * @param config the settings ({@code research.quote.max-chars})
   */
  public CitationVerificationService(ResearchStore store, RuntimeConfigProvider config) {
    this.store = Objects.requireNonNull(store, "ResearchStore cannot be null");
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public GroundedAnswer verify(GroundedAnswer answer, VerifyMode mode, List<ReadDocument> read) {
    int maxQuote = config.getInt("research.quote.max-chars", 300);
    List<ReadDocument> readByThisAnswer = read == null ? List.of() : read;
    List<Claim> checked = new ArrayList<>();
    for (Claim claim : answer.claims()) {
      checked.add(check(claim, maxQuote, readByThisAnswer));
    }
    List<Claim> verified =
        checked.stream().filter(c -> c.status() == ClaimStatus.VERIFIED).toList();
    List<Claim> result = new ArrayList<>(mode == VerifyMode.STRICT ? verified : checked);
    // Unlisted references are marked claims: kept in MARK, never added in STRICT.
    for (String reference :
        mode == VerifyMode.MARK ? ReferenceScanner.references(answer.answer()) : Set.<String>of()) {
      if (!coveredBy(reference, verified)) {
        result.add(
            new Claim(
                reference, ClaimStatus.TO_VALIDATE, ClaimReason.UNLISTED_REFERENCE, List.of()));
      }
    }
    return new GroundedAnswer(render(result, mode), result, answer.sources());
  }

  /** The served prose: the claims after verification, never what the model wrote. */
  private static String render(List<Claim> claims, VerifyMode mode) {
    List<String> lines = new ArrayList<>();
    for (Claim claim : claims) {
      if (claim.status() == ClaimStatus.VERIFIED) {
        lines.add(claim.text());
      } else if (mode == VerifyMode.MARK) {
        lines.add(MARK_PREFIX + claim.text());
      }
    }
    return String.join("\n", lines);
  }

  private Claim check(Claim claim, int maxQuote, List<ReadDocument> read) {
    if (claim.citations().isEmpty()) {
      return downgrade(claim, ClaimReason.NO_SOURCE);
    }
    ClaimReason furthest = ClaimReason.URL_UNRESOLVED;
    // Only the citations that pass both checks are kept as evidence: a claim verified by one
    // citation never vouches for the others, and coverage trusts only what was checked.
    List<Citation> passing = new ArrayList<>();
    for (Citation citation : claim.citations()) {
      Optional<FetchedDocument> document = readDocument(citation, read);
      if (document.isEmpty()) {
        continue;
      }
      furthest = further(furthest, ClaimReason.QUOTE_NOT_FOUND);
      if (citation.quote().length() > maxQuote
          || !QuoteNormalizer.contains(document.get().text(), citation.quote())) {
        continue;
      }
      furthest = further(furthest, ClaimReason.UNSUPPORTED);
      if (supported(claim.text(), citation, document.get())) {
        passing.add(citation);
      }
    }
    return passing.isEmpty()
        ? downgrade(claim, furthest)
        : new Claim(claim.text(), ClaimStatus.VERIFIED, null, passing);
  }

  /**
   * The document a citation names, only when this answer read it, and only through that document's
   * own source (ADR-087 §5): the store is never asked for a document by URL alone.
   */
  private Optional<FetchedDocument> readDocument(Citation citation, List<ReadDocument> read) {
    return read.stream()
        .filter(r -> r.matches(citation))
        .findFirst()
        .flatMap(r -> store.source(r.sourceKey()))
        .map(ResearchSource::id)
        .flatMap(id -> store.document(id, citation.url(), citation.contentHash()));
  }

  /** The checks in the order a citation reaches them; a claim reports the furthest one reached. */
  private static final List<ClaimReason> CHECKS =
      List.of(ClaimReason.URL_UNRESOLVED, ClaimReason.QUOTE_NOT_FOUND, ClaimReason.UNSUPPORTED);

  private static ClaimReason further(ClaimReason current, ClaimReason reached) {
    return CHECKS.indexOf(reached) > CHECKS.indexOf(current) ? reached : current;
  }

  private static Claim downgrade(Claim claim, ClaimReason reason) {
    return new Claim(claim.text(), ClaimStatus.TO_VALIDATE, reason, claim.citations());
  }

  /**
   * Whether every number, date and reference of the claim is in the quote, the cited URL, the
   * document's title or the heading of the quote's section.
   */
  private static boolean supported(String claimText, Citation citation, FetchedDocument document) {
    String heading = sectionHeading(document.text(), citation.quote());
    String scope = citation.quote() + " " + citation.url() + " " + document.title() + " " + heading;
    Set<String> numbers = numbers(QuoteNormalizer.normalize(scope));
    if (!numbers.containsAll(numbers(QuoteNormalizer.normalize(claimText)))) {
      return false;
    }
    // References compare by identity (art. = article, the whole provision, a URL exactly): « art.
    // 1 » is not found in « Article 12 », nor a repository in the prefix of another.
    return ReferenceScanner.keys(scope).containsAll(ReferenceScanner.keys(claimText));
  }

  /**
   * The numbers and dates of a text as whole tokens: « 14 » is not found in « 140 » or « 2024 ».
   */
  private static Set<String> numbers(String text) {
    Set<String> tokens = new LinkedHashSet<>();
    Matcher numbers = NUMBERS.matcher(text);
    while (numbers.find()) {
      tokens.add(numbers.group());
    }
    return tokens;
  }

  /**
   * The heading of the section a quote is in: the label its own paragraph starts with, else the
   * nearest heading-like paragraph above it; empty when the quote cannot be placed.
   */
  static String sectionHeading(String text, String quote) {
    String[] paragraphs = PARAGRAPHS.split(text == null ? "" : text);
    List<String> normalised = new ArrayList<>();
    for (String paragraph : paragraphs) {
      normalised.add(QuoteNormalizer.normalize(paragraph));
    }
    String joined = String.join(" ", normalised);
    String needle = QuoteNormalizer.normalize(quote);
    int at = needle.isEmpty() ? -1 : joined.indexOf(needle);
    if (at < 0) {
      return "";
    }
    int index = 0;
    int offset = 0;
    while (index < normalised.size() - 1 && offset + normalised.get(index).length() < at) {
      offset += normalised.get(index).length() + 1;
      index++;
    }
    Matcher label = SECTION_LABEL.matcher(paragraphs[index].strip());
    if (label.find()) {
      return label.group();
    }
    for (int i = index - 1; i >= 0; i--) {
      String above = paragraphs[i].strip();
      if (isHeading(above)) {
        return above;
      }
    }
    return "";
  }

  private static boolean isHeading(String paragraph) {
    return !paragraph.isEmpty()
        && paragraph.length() <= HEADING_MAX_CHARS
        && !paragraph.matches("(?s).*[.!?;:]$");
  }

  /**
   * A reference is covered when a verified claim states it or its evidence does — by identity, not
   * by substring: a statute by its whole provision (« art. 12 » is covered by « L'article 12 … »,
   * not by « 12 jours » nor by « article 123 »), a URL only by the complete cited URL, exactly as
   * written (a shared prefix is another address), a repository by its name.
   */
  private static boolean coveredBy(String reference, List<Claim> verified) {
    String key = ReferenceScanner.key(reference);
    for (Claim claim : verified) {
      if (ReferenceScanner.keys(claim.text()).contains(key)) {
        return true;
      }
      for (Citation c : claim.citations()) {
        if (ReferenceScanner.key(c.url()).equals(key)
            || ReferenceScanner.keys(c.quote()).contains(key)) {
          return true;
        }
      }
    }
    return false;
  }
}
