package com.krizaka.sovkora.tools.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** robots.txt as RFC 9309 reads it. */
class RobotsRulesTest {

  private static final String FILE =
      """
      # a comment
      User-agent: *
      Disallow: /private/
      Allow: /private/open$

      User-agent: SovkoraResearch
      User-agent: OtherBot
      Disallow: /no-research/
      Allow: /no-research/except
      Disallow: /*.pdf$
      """;

  @Test
  @DisplayName("our own group applies when it exists, not the * group")
  void ourGroupWins() {
    RobotsRules ours = RobotsRules.parse(FILE, "SovkoraResearch");
    assertThat(ours.allows("/private/secret")).isTrue();
    assertThat(ours.allows("/no-research/page")).isFalse();
    assertThat(ours.allows("/no-research/except/x")).isTrue();
    assertThat(ours.allows("/doc.pdf")).isFalse();
    assertThat(ours.allows("/doc.pdf?x=1")).isTrue();
  }

  @Test
  @DisplayName("without our group, the * group applies; the longest match decides; $ anchors")
  void starGroup() {
    RobotsRules star = RobotsRules.parse(FILE, "SomeoneElse");
    assertThat(star.allows("/private/secret")).isFalse();
    assertThat(star.allows("/private/open")).isTrue();
    assertThat(star.allows("/private/open/more")).isFalse();
    assertThat(star.allows("/public")).isTrue();
  }

  @Test
  @DisplayName("Allow wins a tie of equal length; an empty Disallow allows")
  void ties() {
    RobotsRules rules =
        RobotsRules.parse("User-agent: *\nDisallow: /page\nAllow: /page\nDisallow:\n", "x");
    assertThat(rules.allows("/page")).isTrue();
  }

  @Test
  @DisplayName("RFC 9309 §2.2.2: rules and paths compare with unreserved escapes decoded")
  void encodedPaths() {
    RobotsRules star = RobotsRules.parse(FILE, "SomeoneElse");
    assertThat(star.allows("/%70rivate/secret")).isFalse();
    assertThat(star.allows("/%70rivate/%6Fpen")).isTrue();
    RobotsRules escapedRule = RobotsRules.parse("User-agent: *\nDisallow: /%70rivate/\n", "x");
    assertThat(escapedRule.allows("/private/x")).isFalse();
    assertThat(escapedRule.allows("/public/x")).isTrue();
    // A reserved escape keeps its meaning: %2F is not a path separator.
    RobotsRules slash = RobotsRules.parse("User-agent: *\nDisallow: /a/b\n", "x");
    assertThat(slash.allows("/a%2Fb")).isTrue();
  }

  @Test
  @DisplayName("4xx allows everything, 5xx or unreachable allows nothing")
  void unavailableAndUnreachable() {
    assertThat(RobotsRules.allowAll().allows("/anything")).isTrue();
    assertThat(RobotsRules.disallowAll().allows("/")).isFalse();
    assertThat(RobotsRules.disallowAll().allows("/anything")).isFalse();
  }
}
