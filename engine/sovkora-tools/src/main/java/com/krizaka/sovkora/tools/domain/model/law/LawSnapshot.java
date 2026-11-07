package com.krizaka.sovkora.tools.domain.model.law;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a source answered for one jurisdiction: the articles, and the facts that make the import
 * traceable.
 *
 * @param articles the articles found, all languages, targeted instruments only
 * @param languages the languages the source covered; an article of another language is not read as
 *     removed
 * @param datasetUrl the address the dataset or file was read from
 * @param datasetEtag the validator the publisher sent, or null
 * @param datasetSha256 the SHA-256 of what was read (for several files: the digest of their
 *     addresses and digests), or null when nothing was read
 * @param consolidatedTo the day the consolidation is current to, or null when the source does not
 *     say
 * @param notModified true when the publisher answered that the dataset is the one already imported
 */
public record LawSnapshot(
    List<LawArticle> articles,
    Set<String> languages,
    String datasetUrl,
    String datasetEtag,
    String datasetSha256,
    LocalDate consolidatedTo,
    boolean notModified) {

  /** Copies the collections. */
  public LawSnapshot {
    articles = List.copyOf(articles);
    languages = Set.copyOf(languages);
  }

  /**
   * One snapshot from several files read from the same publisher, such as one XML file per act and
   * language. The fingerprint is the digest of each file's address and digest, in order, so that a
   * change in any file changes it.
   *
   * @param datasetUrl the address recorded for the whole read
   * @param files one snapshot per file, each with its address and digest
   * @return the articles of every file, the languages they cover and the latest consolidation day
   */
  public static LawSnapshot merge(String datasetUrl, List<LawSnapshot> files) {
    List<LawArticle> articles = new ArrayList<>();
    Set<String> languages = new HashSet<>();
    StringBuilder digests = new StringBuilder();
    LocalDate consolidatedTo = null;
    for (LawSnapshot file : files) {
      articles.addAll(file.articles());
      languages.addAll(file.languages());
      digests.append(file.datasetUrl()).append(' ').append(file.datasetSha256()).append('\n');
      if (file.consolidatedTo() != null
          && (consolidatedTo == null || file.consolidatedTo().isAfter(consolidatedTo))) {
        consolidatedTo = file.consolidatedTo();
      }
    }
    return new LawSnapshot(
        articles,
        languages,
        datasetUrl,
        null,
        Sha256.hex(digests.toString().getBytes(StandardCharsets.UTF_8)),
        consolidatedTo,
        false);
  }

  /**
   * An answer that the dataset has not changed since the validator given.
   *
   * @param datasetUrl the address asked
   * @param datasetEtag the validator that matched
   * @return a snapshot with no article
   */
  public static LawSnapshot notModified(String datasetUrl, String datasetEtag) {
    return new LawSnapshot(List.of(), Set.of(), datasetUrl, datasetEtag, null, null, true);
  }
}
