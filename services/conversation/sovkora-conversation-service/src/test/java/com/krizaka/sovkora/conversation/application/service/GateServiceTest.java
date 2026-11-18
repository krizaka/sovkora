package com.krizaka.sovkora.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.conversation.domain.model.gate.CostShieldGate;
import com.krizaka.sovkora.service.auth.ServiceTokenProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GateServiceTest {

  private static final String KEY = "gate-test-service-token-key-0123456789";

  @Test
  void signsWithAKeyDerivedFromTheServiceTokenKeyNeverWithItDirectly() throws Exception {
    var token = new GateService(new ServiceTokenProperties(KEY)).evaluate(gate());

    String direct =
        hex(
            KEY.getBytes(StandardCharsets.UTF_8),
            token.gateDecision() + ":" + token.issuedAt().toEpochMilli());
    assertThat(token.gateDecision()).isEqualTo("COST_SHIELD_LOCAL");
    assertThat(token.tokenValue()).hasSize(64).isNotEqualTo(direct);
  }

  private static CostShieldGate gate() {
    return new CostShieldGate(0.5, 0.1, false);
  }

  private static String hex(byte[] key, String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
