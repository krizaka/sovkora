package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Locale;

/**
 * The address a citation shows for an article: the publisher's own page, never the file the text
 * was read from. A page is a template stored with the instrument ({@code
 * law_instrument.official_url_template}) so that a new instrument is data, not code.
 *
 * <p>The placeholders: {@code {ref}} the article reference, {@code {lang}} {@code fr} or {@code
 * en}, {@code {lang3}} {@code fra} or {@code eng}, {@code {acts}} {@code lois} or {@code acts},
 * {@code {section}} {@code article} or {@code section} (Justice Canada's paths differ by language).
 */
public final class OfficialUrls {

  private OfficialUrls() {}

  /**
   * The template of an instrument, for the publishers the corpus reads.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @param instrument the instrument code
   * @return the template, with its placeholders
   */
  public static String template(String jurisdiction, String instrument) {
    return switch (jurisdiction) {
      case "QC" ->
          "https://www.legisquebec.gouv.qc.ca/{lang}/version/lc/" + instrument + "?code=se:{ref}";
      case "CA" ->
          "https://laws-lois.justice.gc.ca/{lang3}/{acts}/" + instrument + "/{section}-{ref}.html";
      default ->
          throw new IllegalArgumentException("no official address is known for " + jurisdiction);
    };
  }

  /**
   * Fills a template for one article and language.
   *
   * @param template the instrument's template
   * @param language {@code fr} or {@code en}
   * @param ref the article reference
   * @return the address
   */
  public static String resolve(String template, String language, String ref) {
    boolean french = "fr".equals(language.toLowerCase(Locale.ROOT));
    return template
        .replace("{lang3}", french ? "fra" : "eng")
        .replace("{lang}", french ? "fr" : "en")
        .replace("{acts}", french ? "lois" : "acts")
        .replace("{section}", french ? "article" : "section")
        .replace("{ref}", ref);
  }

  /**
   * The official page of an article of a publisher the corpus reads.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @param instrument the instrument code
   * @param language {@code fr} or {@code en}
   * @param ref the article reference
   * @return the publisher's address, such as Légis Québec's {@code ?code=se:<ref>}
   */
  public static String of(String jurisdiction, String instrument, String language, String ref) {
    return resolve(template(jurisdiction, instrument), language, ref);
  }
}
