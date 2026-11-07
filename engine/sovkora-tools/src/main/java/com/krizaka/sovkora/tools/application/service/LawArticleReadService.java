package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleQuery;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleRead;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleRead.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.QuoteCheck;
import com.krizaka.sovkora.tools.domain.model.law.StatuteLookup;
import com.krizaka.sovkora.tools.domain.model.law.StatuteVersion;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The law read API's use case (issue #89, ADR-089 §2 and §9): {@code GET /api/v1/law/articles}
 * behind the engine's single door. The host's controller only translates HTTP; every rule is here.
 *
 * <ul>
 *   <li><b>{@code asOf} is required</b> and read as an ISO day ({@code yyyy-MM-dd}); a missing or
 *       unreadable one is refused. No day is ever read from a clock (the UTC-day bug of
 *       yomizaka#309): a caller that wants « today » sends its own calendar day.
 *   <li><b>Fail closed on integrity</b>: the text is served only when it still gives the hash
 *       recorded with it ({@link LawStatuteLookupService}); otherwise {@code LAW_INTEGRITY_ERROR}
 *       with no text.
 *   <li><b>The quote</b>, when asked, is checked against the article text under the corpus's own
 *       normalisation ({@link QuoteCheck}): deterministic, no model.
 *   <li><b>Metered</b> as one {@code RESEARCH} {@code CALL} on the provider {@value
 *       LawCorpusResearchService#PROVIDER} through {@link ResearchMeter}: held before the corpus is
 *       read, <b>settled only when the text is served</b> ({@code FOUND}, whatever the quote
 *       check), released on every other outcome. An invalid question and a switched-off corpus are
 *       answered before anything is held. With no meter wired the read is refused, never served
 *       unmetered.
 * </ul>
 */
public class LawArticleReadService {

  private static final Pattern ISO_DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final LawStatuteLookupService lookups;
  private final LawSettings settings;
  private final ResearchMeter meter;

  /**
   * Creates the use case.
   *
   * @param lookups the dated, integrity-checked lookup
   * @param settings the {@code law.*} settings
   * @param meter the research meter; null when none is wired, and then every read is refused
   */
  public LawArticleReadService(
      LawStatuteLookupService lookups, LawSettings settings, ResearchMeter meter) {
    this.lookups = Objects.requireNonNull(lookups, "LawStatuteLookupService");
    this.settings = Objects.requireNonNull(settings, "LawSettings");
    this.meter = meter;
  }

  /**
   * Reads an article as it was in force on a day.
   *
   * @param query the question, as sent
   * @param actorId who is billed (the caller's user id)
   * @return the answer; its text only when {@code FOUND}
   * @throws IllegalArgumentException when a part of the question is missing or unreadable; nothing
   *     is held
   */
  public LawArticleRead read(LawArticleQuery query, String actorId) {
    Objects.requireNonNull(query, "query");
    LocalDate asOf = asOf(query.asOf());
    requirePresent(query.jurisdiction(), "jurisdiction");
    requirePresent(query.instrument(), "instrument");
    requirePresent(query.ref(), "ref");
    requirePresent(query.language(), "language");
    String language = query.language().trim().toLowerCase(Locale.ROOT);
    if (!"fr".equals(language) && !"en".equals(language)) {
      throw new IllegalArgumentException("language must be fr or en");
    }
    if (query.quote() != null && query.quote().isBlank()) {
      throw new IllegalArgumentException("quote, when sent, is not blank");
    }
    requirePresent(actorId, "actorId");

    if (!settings.corpusEnabled()) {
      return LawArticleRead.without(Outcome.LAW_CORPUS_DISABLED, null);
    }
    if (meter == null) {
      return LawArticleRead.without(Outcome.LAW_METERING_UNAVAILABLE, null);
    }
    String readId = UUID.randomUUID().toString();
    ResearchMeter.Hold hold;
    try {
      hold = meter.hold(readId, actorId, LawCorpusResearchService.PROVIDER);
    } catch (ResearchMeter.BudgetExceededException refused) {
      return LawArticleRead.without(Outcome.LAW_BUDGET_EXCEEDED, null);
    }

    StatuteLookup lookup;
    try {
      lookup =
          lookups.lookupStatute(
              query.jurisdiction(), query.instrument(), query.ref(), language, asOf);
    } catch (RuntimeException failure) {
      release(hold, "the corpus could not be read", failure);
      throw failure;
    }
    return switch (lookup.outcome()) {
      case FOUND -> served(lookup.version(), query.quote(), readId, hold);
      case LAW_VERSION_UNKNOWN -> released(hold, Outcome.LAW_VERSION_UNKNOWN, readId);
      case LAW_CORPUS_DISABLED -> released(hold, Outcome.LAW_CORPUS_DISABLED, readId);
      case LAW_INTEGRITY_ERROR -> released(hold, Outcome.LAW_INTEGRITY_ERROR, readId);
    };
  }

  private static LawArticleRead served(
      StatuteVersion version, String quote, String readId, ResearchMeter.Hold hold) {
    QuoteCheck check = quote == null ? null : QuoteCheck.of(version.text(), quote);
    hold.settle();
    return new LawArticleRead(Outcome.FOUND, readId, version, check);
  }

  private static LawArticleRead released(ResearchMeter.Hold hold, Outcome outcome, String readId) {
    hold.release(outcome.name());
    return LawArticleRead.without(outcome, readId);
  }

  /** A failed release never hides the failure that caused it. */
  private static void release(ResearchMeter.Hold hold, String reason, RuntimeException failure) {
    try {
      hold.release(reason);
    } catch (RuntimeException releaseFailure) {
      failure.addSuppressed(releaseFailure);
    }
  }

  private static LocalDate asOf(String value) {
    if (value == null || !ISO_DAY.matcher(value.trim()).matches()) {
      throw new IllegalArgumentException("asOf is required, as an ISO day (yyyy-MM-dd)");
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("asOf is required, as an ISO day (yyyy-MM-dd)", e);
    }
  }

  private static void requirePresent(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
