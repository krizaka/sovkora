package com.krizaka.sovkora.automationservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [ADR-035 amendment, ADR-084] The only request this service answers without authentication is the
 * signed inbound webhook, {@code POST /hooks/{triggerId}}: one verb, one path with one segment, no
 * wildcard. A {@code permitAll} anywhere else, or a looser matcher, fails the build.
 */
class SecurityExposureTest {

  private static final Pattern ANY_PERMIT_ALL = Pattern.compile("permitAll\\s*\\(");
  private static final Pattern PERMIT_ALL_MATCHER =
      Pattern.compile("requestMatchers\\(([^)]*)\\)\\s*\\.permitAll\\s*\\(");
  private static final String ONLY_OPEN_MATCHER = "HttpMethod.POST,\"/hooks/{triggerId}\"";

  private static String securityConfig() throws IOException {
    Path file =
        Path.of(
            System.getProperty("user.dir"),
            "src/main/java/com/krizaka/sovkora/automationservice/infrastructure/config/SecurityConfig.java");
    // Comments are explanations, not code: the rule is about what is configured.
    return Files.readString(file).replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", "");
  }

  @Test
  @DisplayName("the single permitAll of the service is POST /hooks/{triggerId}")
  void onlyTheWebhookIsOpen() throws IOException {
    String code = securityConfig();
    int all = count(ANY_PERMIT_ALL, code);
    List<String> opened = new ArrayList<>();
    Matcher matcher = PERMIT_ALL_MATCHER.matcher(code);
    while (matcher.find()) {
      opened.add(matcher.group(1).replaceAll("\\s+", ""));
    }
    assertThat(all)
        .as("every permitAll must follow a requestMatchers(...) naming what it opens")
        .isEqualTo(opened.size());
    assertThat(opened)
        .as("the only anonymous request is the signed webhook, never a wildcard")
        .allMatch(ONLY_OPEN_MATCHER::equals);
    assertThat(opened.size()).as("and it is opened at most once").isLessThanOrEqualTo(1);
  }

  @Test
  @DisplayName("the filter chain starts from the Krizaka baseline (KIT-001)")
  void startsFromTheBaseline() throws IOException {
    assertThat(securityConfig()).contains("SecurityBaseline.apply(");
  }

  private static int count(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int n = 0;
    while (matcher.find()) {
      n++;
    }
    return n;
  }
}
