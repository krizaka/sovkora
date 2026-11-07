package com.krizaka.sovkora.assets.infrastructure.tool;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.crypto.SecretSealer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Seals a new subject pepper for the external checks (krizaka/sovkora-packs#138): the key of the
 * HMAC that names a subject in the check audit.
 *
 * <pre>
 * java -cp … CheckSubjectPepperTool seal &lt;keyfile&gt;
 * </pre>
 *
 * <p>It draws {@value #PEPPER_BYTES} random bytes, encodes them base64url, seals that text with the
 * platform's master keyring for the pepper's own scope ({@link SecretSealer}, ADR-054, the
 * mechanism of the MCP tokens and the trigger secrets) and prints the one statement that writes the
 * sealed value to {@code orazaka_runtime_config} under {@value #SETTING_KEY}. The pepper itself is
 * never printed, never written in clear, and nobody needs to know it: the engine opens it at each
 * check ({@code SealedCheckSubjectPepperAdapter}).
 *
 * <p>A {@code main}, by hand, as {@link AssetEncryptionTool}: creating a key is done once by a
 * person, and an HTTP surface for "make me a key" would be one more thing to guard. Changing the
 * pepper changes every subject reference written after it (the old ones stay as they were).
 */
public final class CheckSubjectPepperTool {

  /** The setting that holds the sealed pepper. */
  public static final String SETTING_KEY = "checks.subject-pepper";

  /** The owner the pepper is sealed for: a value sealed for another scope does not open. */
  public static final UUID SCOPE =
      UUID.nameUUIDFromBytes(
          "sovkora:platform:checks.subject-pepper".getBytes(StandardCharsets.UTF_8));

  /** The pepper's length: the HMAC-SHA-256 key the check service requires at least. */
  public static final int PEPPER_BYTES = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Pattern SEALED_FORM = Pattern.compile("v1(\\.[A-Za-z0-9_-]+){4}");

  private CheckSubjectPepperTool() {}

  /**
   * @param args {@code seal <keyfile>}
   */
  public static void main(String[] args) {
    if (args.length != 2 || !"seal".equals(args[0])) {
      System.err.println("usage: CheckSubjectPepperTool seal <keyfile>");
      System.exit(2);
      return;
    }
    String sealed = sealNew(new FileMasterKeyProvider(Path.of(args[1])));
    System.out.println(upsertStatement(sealed));
  }

  /**
   * Draws a new pepper and seals it.
   *
   * @param keys the platform's master keyring
   * @return the sealed form, {@code v1.<keyId>.<wrapped key>.<nonce>.<ciphertext>}
   */
  public static String sealNew(MasterKeyProvider keys) {
    byte[] pepper = new byte[PEPPER_BYTES];
    RANDOM.nextBytes(pepper);
    try {
      return new SecretSealer(keys)
          .seal(SCOPE, Base64.getUrlEncoder().withoutPadding().encodeToString(pepper));
    } finally {
      Arrays.fill(pepper, (byte) 0);
    }
  }

  /**
   * The statement that writes a sealed pepper.
   *
   * @param sealed the sealed form
   * @return an upsert of the setting row
   */
  public static String upsertStatement(String sealed) {
    // Base64url parts only: the value is safe inside a SQL literal.
    if (sealed == null || !SEALED_FORM.matcher(sealed).matches()) {
      throw new IllegalArgumentException("not a sealed value");
    }
    return "INSERT INTO orazaka_runtime_config (config_key, config_value, value_type, description)"
        + " VALUES ('"
        + SETTING_KEY
        + "', '"
        + sealed
        + "', 'sealed', 'Sealed pepper of the external checks'' subject reference (HMAC-SHA-256)."
        + " Absent: no check runs.')"
        + " ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value,"
        + " value_type = EXCLUDED.value_type;";
  }
}
