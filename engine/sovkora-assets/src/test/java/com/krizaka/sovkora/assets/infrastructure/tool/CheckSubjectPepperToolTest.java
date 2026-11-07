package com.krizaka.sovkora.assets.infrastructure.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.crypto.SecretSealer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The operator seals a new subject pepper without ever seeing it (krizaka/sovkora-packs#138). */
class CheckSubjectPepperToolTest {

  private static FileMasterKeyProvider keyring(Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    return new FileMasterKeyProvider(keyFile);
  }

  @Test
  @DisplayName("the sealed value opens, for the pepper's scope only, to 32 random bytes")
  void sealsThirtyTwoRandomBytesForItsScope(@TempDir Path dir) throws Exception {
    FileMasterKeyProvider keys = keyring(dir);

    String sealed = CheckSubjectPepperTool.sealNew(keys);

    SecretSealer sealer = new SecretSealer(keys);
    assertThat(sealer.isSealed(sealed)).isTrue();
    byte[] pepper =
        Base64.getUrlDecoder().decode(sealer.open(CheckSubjectPepperTool.SCOPE, sealed));
    assertThat(pepper).hasSize(CheckSubjectPepperTool.PEPPER_BYTES);
    assertThatThrownBy(() -> sealer.open(UUID.randomUUID(), sealed))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("each seal is a new pepper")
  void eachSealIsANewPepper(@TempDir Path dir) throws Exception {
    FileMasterKeyProvider keys = keyring(dir);
    SecretSealer sealer = new SecretSealer(keys);

    String first = sealer.open(CheckSubjectPepperTool.SCOPE, CheckSubjectPepperTool.sealNew(keys));
    String second = sealer.open(CheckSubjectPepperTool.SCOPE, CheckSubjectPepperTool.sealNew(keys));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("the sealed value fits the settings column (VARCHAR 255)")
  void fitsTheColumn(@TempDir Path dir) throws Exception {
    assertThat(CheckSubjectPepperTool.sealNew(keyring(dir)).length()).isLessThanOrEqualTo(255);
  }

  @Test
  @DisplayName("the statement it prints writes the sealed value under the setting key, and only it")
  void theStatementCarriesTheSealedValueOnly(@TempDir Path dir) throws Exception {
    FileMasterKeyProvider keys = keyring(dir);
    String sealed = CheckSubjectPepperTool.sealNew(keys);
    String pepperText = new SecretSealer(keys).open(CheckSubjectPepperTool.SCOPE, sealed);

    String statement = CheckSubjectPepperTool.upsertStatement(sealed);

    assertThat(statement)
        .contains("orazaka_runtime_config")
        .contains("'checks.subject-pepper'")
        .contains("'" + sealed + "'")
        .contains("'sealed'")
        .contains("ON CONFLICT (config_key) DO UPDATE")
        .doesNotContain(pepperText);
  }

  @Test
  @DisplayName("the scope is the documented platform scope of the pepper")
  void theScopeIsDocumented() {
    assertThat(CheckSubjectPepperTool.SCOPE)
        .isEqualTo(
            UUID.nameUUIDFromBytes(
                "sovkora:platform:checks.subject-pepper".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("it writes only a value in the sealed form, so nothing else reaches the statement")
  void refusesAnythingButTheSealedForm() {
    assertThatThrownBy(() -> CheckSubjectPepperTool.upsertStatement("plain-pepper"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CheckSubjectPepperTool.upsertStatement("v1.a.b.c.d'; DROP TABLE x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CheckSubjectPepperTool.upsertStatement(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
