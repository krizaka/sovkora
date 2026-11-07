package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawChange;

/**
 * The review file of one change, as it goes into a pull request of the rulesets repository.
 *
 * @param branch the branch the request is opened from
 * @param path the file added on that branch
 * @param title the pull request title
 * @param body the pull request description
 * @param content the file content: the change, its hashes, its citation and the diff
 */
public record LawChangeReview(
    String branch, String path, String title, String body, String content) {

  /**
   * Renders the review of a change.
   *
   * @param change the detected change
   * @return the branch, file, title, description and content for it
   */
  public static LawChangeReview of(LawChange change) {
    LawArticle subject = change.subject();
    String slug =
        slug(
            change.detectedOn()
                + "-"
                + change.kind().name().toLowerCase()
                + "-"
                + subject.instrument()
                + "-"
                + subject.ref()
                + "-"
                + subject.language());
    String title =
        "Law watch: "
            + change.kind()
            + " "
            + subject.instrument()
            + " art. "
            + subject.ref()
            + " ("
            + subject.language()
            + ")";
    String previous = change.previous() == null ? "(none)" : change.previous().text();
    String current =
        change.current() == null ? "(removed from the source)" : change.current().text();
    String hashBefore =
        change.previous() == null ? "(none)" : change.previous().contentHash().hex();
    String hashNow = change.current() == null ? "(none)" : change.current().contentHash().hex();
    String content =
        "# "
            + title
            + "\n\n"
            + "- Detected: "
            + change.detectedOn()
            + "\n"
            + "- Kind: "
            + change.kind()
            + "\n"
            + "- Citation (official text): "
            + subject.officialUrl()
            + "\n"
            + "- Read from: "
            + subject.sourceUrl()
            + "\n"
            + "- SHA-256 before: "
            + hashBefore
            + "\n"
            + "- SHA-256 now: "
            + hashNow
            + "\n\n"
            + "## Diff\n\n"
            + "```diff\n"
            + "- "
            + previous
            + "\n"
            + "+ "
            + current
            + "\n```\n\n"
            + "A reviewer updates the rulesets that cite this article (their `citation` and pinned"
            + " hash) or flags the rules for review. Nothing is judged from this file.\n";
    String body =
        "Automatic law watch. The official text of "
            + subject.instrument()
            + " art. "
            + subject.ref()
            + " ("
            + subject.language()
            + ") changed or left the source. The review file has the diff and the citation: "
            + subject.officialUrl()
            + "\n\n"
            + "The rulesets that cite this article must be reviewed before the next release.\n\n"
            + "🤖 Generated with [Claude Code](https://claude.com/claude-code)";
    return new LawChangeReview(
        "law-watch/" + slug, "law-watch/" + slug + ".md", title, body, content);
  }

  private static String slug(String raw) {
    return raw.replaceAll("[^A-Za-z0-9.-]+", "-").replaceAll("-+", "-");
  }
}
