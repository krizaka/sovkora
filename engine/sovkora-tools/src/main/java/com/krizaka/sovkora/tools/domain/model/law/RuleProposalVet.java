package com.krizaka.sovkora.tools.domain.model.law;

import com.krizaka.sovkora.core.domain.model.research.QuoteNormalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * {@code vet} (ADR-089 §3): the deterministic checks every proposed rule passes before it is
 * written, with no model. A rule that fails one is still written, as {@code REJECTED_AUTO} with its
 * reasons, so the reviewer sees what the model tried.
 *
 * <ol>
 *   <li>the shape of a ruleset rule ({@link VetReason#SCHEMA_INVALID}); past {@code
 *       law.derive.max-rules-per-kind} nothing else is read ({@link VetReason#OVER_RULE_LIMIT});
 *   <li>its patterns: they compile as the worker runs them and do not match the empty text;
 *   <li>its law: each block names the version in force on the day, and its quote is a passage of
 *       that text;
 *   <li>every number its words state is in one of its quotes;
 *   <li>a {@code FORBIDDEN_PATTERN} or {@code JUDGMENT} rule declares its subject;
 *   <li>a deterministic rule tells its fixture pair apart (ADR-072), evaluated as the platform's
 *       installer and the pack's worker evaluate the two pattern checks.
 * </ol>
 *
 * A derived rule uses only {@code REQUIRED_PATTERN}, {@code FORBIDDEN_PATTERN} or {@code JUDGMENT}:
 * the numeric checks stay written by hand.
 */
public final class RuleProposalVet {

  /** The version of these checks, written in every report. */
  public static final String VERSION = "1";

  static final String REQUIRED_PATTERN = "REQUIRED_PATTERN";
  static final String FORBIDDEN_PATTERN = "FORBIDDEN_PATTERN";
  static final String DETERMINISTIC = "DETERMINISTIC";
  static final String JUDGMENT = "JUDGMENT";
  static final String ADVISORY = "ADVISORY";

  private static final Set<String> SEVERITIES = Set.of("BLOCKING", "MAJOR", ADVISORY);
  private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

  private final InForceArticles articles;
  private final LocalDate asOf;
  private final int maxRules;

  /**
   * Creates the vet of one derivation.
   *
   * @param articles the corpus at the derivation's day
   * @param asOf that day, written in each report
   * @param maxRules {@code law.derive.max-rules-per-kind}
   */
  public RuleProposalVet(InForceArticles articles, LocalDate asOf, int maxRules) {
    this.articles = Objects.requireNonNull(articles, "articles");
    this.asOf = Objects.requireNonNull(asOf, "asOf");
    if (maxRules < 1) {
      throw new IllegalArgumentException("maxRules must be at least 1");
    }
    this.maxRules = maxRules;
  }

  /**
   * Vets the rules of a derivation, in the order they were proposed.
   *
   * @param rules the proposed rules
   * @return one vetted proposal per rule, in the same order
   */
  public List<VettedProposal> vet(List<ProposedRule> rules) {
    List<VettedProposal> vetted = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ProposedRule rule : rules) {
      List<VetFinding> findings = new ArrayList<>();
      if (vetted.size() >= maxRules) {
        findings.add(
            new VetFinding(
                VetReason.OVER_RULE_LIMIT,
                "the derivation already proposed " + maxRules + " rules"));
      } else {
        findings.addAll(check(rule, seen));
      }
      if (rule.ruleId() != null) {
        seen.add(rule.ruleId());
      }
      vetted.add(new VettedProposal(rule, new VetReport(VERSION, asOf.toString(), findings)));
    }
    return List.copyOf(vetted);
  }

  private List<VetFinding> check(ProposedRule rule, Set<String> seen) {
    List<VetFinding> shape = shape(rule, seen);
    if (!shape.isEmpty()) {
      return shape;
    }
    List<VetFinding> findings = new ArrayList<>(patterns(rule));
    boolean patternsUsable = findings.isEmpty();
    findings.addAll(law(rule));
    findings.addAll(numbers(rule));
    boolean needsSubject = JUDGMENT.equals(rule.method()) || isForbidden(rule);
    if (needsSubject && blank(rule.subject())) {
      findings.add(
          new VetFinding(
              VetReason.SUBJECT_MISSING,
              "a " + kindOf(rule) + " rule names what it is about, so a PASS can quote it"));
    }
    if (patternsUsable && DETERMINISTIC.equals(rule.method())) {
      findings.addAll(fixtures(rule));
    }
    return findings;
  }

  // ── 1. the shape ───────────────────────────────────────────────────────────────

  private static void methodShape(ProposedRule rule, List<String> missing) {
    if (JUDGMENT.equals(rule.method())) {
      requireText(rule.question(), "question", missing);
      if (!ADVISORY.equals(rule.severity())) {
        missing.add("severity ADVISORY: a JUDGMENT proposal is always ADVISORY");
      }
    } else if (DETERMINISTIC.equals(rule.method())) {
      deterministicShape(rule, missing);
    } else {
      missing.add("method (DETERMINISTIC or JUDGMENT)");
    }
  }

  private static void lawShape(ProposedRule rule, List<String> missing) {
    if (rule.law().isEmpty()) {
      missing.add("law: at least one article it rests on");
    }
    for (LawBlock block : rule.law()) {
      if (!complete(block)) {
        missing.add("law: instrument, ref, language (fr or en), contentHash and quote");
        break;
      }
    }
  }

  private static List<VetFinding> shape(ProposedRule rule, Set<String> seen) {
    List<String> missing = new ArrayList<>();
    if (!isRuleId(rule.ruleId())) {
      missing.add("ruleId (UPPER-CASE-WITH-DASHES)");
    } else if (seen.contains(rule.ruleId())) {
      missing.add("a ruleId of its own (" + rule.ruleId() + " is proposed twice)");
    }
    requireText(rule.title(), "title", missing);
    requireText(rule.citation(), "citation", missing);
    if (blank(rule.sourceUrl()) || !rule.sourceUrl().startsWith("https://")) {
      missing.add("sourceUrl (https)");
    }
    if (rule.severity() == null || !SEVERITIES.contains(rule.severity())) {
      missing.add("severity (BLOCKING, MAJOR or ADVISORY)");
    }
    methodShape(rule, missing);
    lawShape(rule, missing);
    return missing.isEmpty()
        ? List.of()
        : List.of(
            new VetFinding(VetReason.SCHEMA_INVALID, "missing " + String.join("; ", missing)));
  }

  private static void deterministicShape(ProposedRule rule, List<String> missing) {
    RuleCheck check = rule.check();
    if (check == null
        || !(REQUIRED_PATTERN.equals(check.type()) || FORBIDDEN_PATTERN.equals(check.type()))) {
      missing.add("check.type (REQUIRED_PATTERN or FORBIDDEN_PATTERN)");
    } else if (blank(check.pattern())) {
      missing.add("check.pattern");
    }
    requireText(rule.onPass(), "onPass", missing);
    requireText(rule.onFail(), "onFail", missing);
    RuleFixtures fixtures = rule.fixtures();
    if (fixtures == null
        || fixtures.pass().isEmpty()
        || fixtures.fail().isEmpty()
        || fixtures.pass().stream().anyMatch(RuleProposalVet::blank)
        || fixtures.fail().stream().anyMatch(RuleProposalVet::blank)) {
      missing.add("fixtures: a pass and a fail fragment, none blank");
    }
  }

  /**
   * UPPER-CASE-WITH-DASHES: at least two parts, letters and digits, the first starting with a
   * letter.
   */
  private static boolean isRuleId(String ruleId) {
    if (blank(ruleId) || !Character.isUpperCase(ruleId.charAt(0))) {
      return false;
    }
    String[] parts = ruleId.split("-", -1);
    return parts.length >= 2
        && java.util.Arrays.stream(parts)
            .allMatch(
                part ->
                    !part.isEmpty()
                        && part.chars()
                            .allMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')));
  }

  private static boolean complete(LawBlock block) {
    return block != null
        && !blank(block.instrument())
        && !blank(block.ref())
        && ("fr".equals(block.language()) || "en".equals(block.language()))
        && block.contentHash() != null
        && HASH.matcher(block.contentHash()).matches()
        && !blank(block.quote());
  }

  // ── 2. the patterns ────────────────────────────────────────────────────────────

  private static List<VetFinding> patterns(ProposedRule rule) {
    List<VetFinding> findings = new ArrayList<>();
    if (rule.check() != null && DETERMINISTIC.equals(rule.method())) {
      pattern("check.pattern", rule.check().pattern(), findings);
    }
    if (!blank(rule.subject())) {
      pattern("subject", rule.subject(), findings);
    }
    return findings;
  }

  private static void pattern(String where, String pattern, List<VetFinding> findings) {
    Optional<String> problem = WorkerRegex.problem(pattern);
    if (problem.isPresent()) {
      findings.add(new VetFinding(VetReason.REGEX_INVALID, where + " " + problem.get()));
    } else if (WorkerRegex.matchesEmpty(pattern)) {
      findings.add(
          new VetFinding(
              VetReason.REGEX_MATCHES_EMPTY,
              where + " matches the empty text, so it finds something in every document"));
    }
  }

  // ── 3. the law ─────────────────────────────────────────────────────────────────

  private List<VetFinding> law(ProposedRule rule) {
    List<VetFinding> findings = new ArrayList<>();
    for (LawBlock block : rule.law()) {
      String article = block.instrument() + " art. " + block.ref() + " (" + block.language() + ")";
      Optional<StatuteVersion> inForce =
          articles.inForce(block.instrument(), block.ref(), block.language());
      if (inForce.isEmpty()) {
        findings.add(
            new VetFinding(
                VetReason.LAW_VERSION_UNKNOWN,
                article + ": the corpus holds no version in force on " + asOf));
        continue;
      }
      StatuteVersion version = inForce.get();
      if (!version.contentHash().equals(block.contentHash())) {
        findings.add(
            new VetFinding(
                VetReason.LAW_VERSION_UNKNOWN,
                article
                    + ": the version cited ("
                    + block.contentHash().substring(0, 12)
                    + ") is not the one in force on "
                    + asOf
                    + " ("
                    + version.contentHash().substring(0, 12)
                    + ")"));
        continue;
      }
      if (!QuoteNormalizer.contains(version.text(), block.quote())) {
        findings.add(
            new VetFinding(
                VetReason.QUOTE_NOT_IN_ARTICLE, article + ": the quote is not in its text"));
      }
    }
    return findings;
  }

  // ── 4. the numbers ─────────────────────────────────────────────────────────────

  private static List<VetFinding> numbers(ProposedRule rule) {
    Set<String> supported = new HashSet<>();
    Set<String> refs = new HashSet<>();
    for (LawBlock block : rule.law()) {
      supported.addAll(RuleFigures.of(block.quote()));
      refs.addAll(RuleFigures.of(block.ref()));
    }
    Set<String> stated = new HashSet<>();
    for (String words :
        new String[] {rule.title(), rule.question(), rule.onPass(), rule.onFail()}) {
      stated.addAll(RuleFigures.of(words));
    }
    // An article number the rule names (« art. 1931 ») is a reference, not a requirement.
    stated.removeAll(refs);
    stated.removeAll(supported);
    if (stated.isEmpty()) {
      return List.of();
    }
    return List.of(
        new VetFinding(
            VetReason.UNSUPPORTED_NUMBER,
            "the rule states " + new TreeSet<>(stated) + ", which no quote states"));
  }

  // ── 6. the fixtures ────────────────────────────────────────────────────────────

  private static List<VetFinding> fixtures(ProposedRule rule) {
    String pattern = rule.check().pattern();
    boolean required = REQUIRED_PATTERN.equals(rule.check().type());
    List<String> wrong = new ArrayList<>();
    // REQUIRED: the pass fragments say it, the fail ones do not. FORBIDDEN: the other way round.
    Predicate<String> passes =
        text -> WorkerRegex.finds(pattern, text) == required && addressed(rule, text);
    for (String text : rule.fixtures().pass()) {
      if (!passes.test(text)) {
        wrong.add("PASS expected on « " + abbreviate(text) + " »");
      }
    }
    for (String text : rule.fixtures().fail()) {
      if (WorkerRegex.finds(pattern, text) == required) {
        wrong.add("FAIL expected on « " + abbreviate(text) + " »");
      }
    }
    return wrong.isEmpty()
        ? List.of()
        : List.of(new VetFinding(VetReason.FIXTURES_INDISTINCT, String.join("; ", wrong)));
  }

  /**
   * Whether a pass fragment of a forbidden-wording rule addresses its subject: without it the
   * worker answers NOT_APPLICABLE, never PASS (#127). A rule with no subject is already rejected
   * for that, and its pattern is still checked here.
   */
  private static boolean addressed(ProposedRule rule, String text) {
    return !isForbidden(rule) || blank(rule.subject()) || WorkerRegex.finds(rule.subject(), text);
  }

  // ── helpers ────────────────────────────────────────────────────────────────────

  private static boolean isForbidden(ProposedRule rule) {
    return DETERMINISTIC.equals(rule.method())
        && rule.check() != null
        && FORBIDDEN_PATTERN.equals(rule.check().type());
  }

  private static String kindOf(ProposedRule rule) {
    return JUDGMENT.equals(rule.method()) ? JUDGMENT : FORBIDDEN_PATTERN;
  }

  private static void requireText(String value, String name, List<String> missing) {
    if (blank(value)) {
      missing.add(name);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String abbreviate(String text) {
    return text.length() <= 72 ? text : text.substring(0, 72) + "…";
  }
}
