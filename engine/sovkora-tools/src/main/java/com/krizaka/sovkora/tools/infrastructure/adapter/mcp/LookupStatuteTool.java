package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.tools.application.service.LawStatuteLookupService;
import com.krizaka.sovkora.tools.domain.model.law.LookupStatuteRequest;
import com.krizaka.sovkora.tools.domain.model.law.LookupStatuteResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * The {@code lookupStatute} tool of grounded research (ADR-087, ADR-089 §2): the text of an article
 * as it was in force on a day, from the dated corpus, with the hash and the official address to
 * cite. Never logs the question or the text.
 */
public final class LookupStatuteTool
    implements Function<LookupStatuteRequest, LookupStatuteResponse> {

  /** The name the model calls the tool by. */
  public static final String NAME = "lookupStatute";

  /** What the model reads to decide when to call it. */
  public static final String DESCRIPTION =
      "Returns the text of one article of a statute or regulation as it was in force on a given"
          + " day (asOf), with its content hash, the day it took effect, the official address to"
          + " cite and the import that brought it in. Answers LAW_VERSION_UNKNOWN when the corpus"
          + " holds no version in force on that day; never guess a text from a later version.";

  private final LawStatuteLookupService lookups;

  /**
   * Creates the tool.
   *
   * @param lookups the lookup
   */
  public LookupStatuteTool(LawStatuteLookupService lookups) {
    this.lookups = lookups;
  }

  @Override
  public LookupStatuteResponse apply(LookupStatuteRequest request) {
    try {
      LocalDate asOf =
          request.asOf() == null || request.asOf().isBlank()
              ? null
              : LocalDate.parse(request.asOf());
      return LookupStatuteResponse.of(
          lookups.lookupStatute(
              request.jurisdiction(),
              request.instrument(),
              request.reference(),
              request.language(),
              asOf));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      return LookupStatuteResponse.invalid();
    }
  }
}
