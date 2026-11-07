package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Known-answer tests of the Standard Webhooks scheme (the published test vector). */
class WebhookSignatureTest {

  private static final byte[] KEY = Base64.getDecoder().decode("MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw");
  private static final String ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
  private static final String TIMESTAMP = "1614265330";
  private static final byte[] BODY = "{\"test\": 2432232314}".getBytes(StandardCharsets.UTF_8);
  private static final String EXPECTED = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

  @Test
  @DisplayName("it produces the signature of the Standard Webhooks test vector")
  void knownAnswer() {
    assertThat(WebhookSignature.sign(KEY, ID, TIMESTAMP, BODY)).isEqualTo(EXPECTED);
  }

  @Test
  @DisplayName("it verifies that signature, alone or among others in a rotation list")
  void verifies() {
    assertThat(WebhookSignature.verify(KEY, EXPECTED, ID, TIMESTAMP, BODY)).isTrue();
    assertThat(
            WebhookSignature.verify(KEY, "v1,AAAA " + EXPECTED + " v2,zzzz", ID, TIMESTAMP, BODY))
        .isTrue();
  }

  @Test
  @DisplayName("it refuses a different id, timestamp, body, key, version or a malformed header")
  void refuses() {
    assertThat(WebhookSignature.verify(KEY, EXPECTED, "msg_other", TIMESTAMP, BODY)).isFalse();
    assertThat(WebhookSignature.verify(KEY, EXPECTED, ID, "1614265331", BODY)).isFalse();
    assertThat(
            WebhookSignature.verify(
                KEY,
                EXPECTED,
                ID,
                TIMESTAMP,
                "{\"test\": 2432232315}".getBytes(StandardCharsets.UTF_8)))
        .isFalse();
    assertThat(WebhookSignature.verify(new byte[32], EXPECTED, ID, TIMESTAMP, BODY)).isFalse();
    assertThat(WebhookSignature.verify(KEY, EXPECTED.replace("v1,", "v2,"), ID, TIMESTAMP, BODY))
        .isFalse();
    assertThat(WebhookSignature.verify(KEY, "v1,@@@@", ID, TIMESTAMP, BODY)).isFalse();
    assertThat(WebhookSignature.verify(KEY, "", ID, TIMESTAMP, BODY)).isFalse();
    assertThat(WebhookSignature.verify(KEY, null, ID, TIMESTAMP, BODY)).isFalse();
  }
}
