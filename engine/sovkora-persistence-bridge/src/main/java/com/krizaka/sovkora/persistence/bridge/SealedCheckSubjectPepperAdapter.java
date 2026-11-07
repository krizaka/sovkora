package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckSubjectPepper;
import com.krizaka.sovkora.crypto.SecretSealer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The subject pepper of the external checks, read from its sealed setting (krizaka/sovkora-packs
 * #138): the row {@value #SETTING_KEY} of {@code orazaka_runtime_config} holds the pepper sealed
 * with the platform's master keyring for its own scope ({@link SecretSealer}, ADR-054 — the
 * mechanism of the MCP tokens), never an environment variable, never the code.
 *
 * <p>The sealed value opens to base64url text, and that text decodes to the pepper's bytes. The
 * operator writes it with {@code CheckSubjectPepperTool seal <keyfile>} (sovkora-assets), which
 * draws a new random pepper and prints the statement, so nobody ever sees the pepper itself.
 *
 * <p>It is read at each call, so a new pepper takes effect without a restart. Every way it can fail
 * answers empty, and then no check runs: no row, a blank one, a value in clear (never taken as the
 * pepper), one that does not open (another scope, another keyring, tampered), no keyring in the
 * host, or fewer than {@value #MIN_BYTES} bytes. A warning names the key and the reason, never the
 * value, the sealed text or an exception's message.
 */
@Service
class SealedCheckSubjectPepperAdapter implements CheckSubjectPepper {

  private static final Logger log = LoggerFactory.getLogger(SealedCheckSubjectPepperAdapter.class);

  /** The setting that holds the sealed pepper. */
  static final String SETTING_KEY = "checks.subject-pepper";

  /** The owner the pepper is sealed for: a value sealed for another scope does not open. */
  static final UUID SCOPE =
      UUID.nameUUIDFromBytes(
          "sovkora:platform:checks.subject-pepper".getBytes(StandardCharsets.UTF_8));

  /** The shortest pepper the HMAC-SHA-256 is keyed with. */
  static final int MIN_BYTES = 32;

  private static final String OFF = ": external checks stay off";

  private final RuntimeConfigProvider settings;
  private final ObjectProvider<MasterKeyProvider> keys;

  SealedCheckSubjectPepperAdapter(
      RuntimeConfigProvider settings, ObjectProvider<MasterKeyProvider> keys) {
    this.settings = Objects.requireNonNull(settings, "RuntimeConfigProvider cannot be null");
    this.keys = Objects.requireNonNull(keys, "MasterKeyProvider provider cannot be null");
  }

  @Override
  public Optional<byte[]> pepper() {
    String stored = settings.getString(SETTING_KEY, null);
    if (stored == null || stored.isBlank()) {
      return Optional.empty();
    }
    MasterKeyProvider keyring = keys.getIfAvailable();
    if (keyring == null) {
      log.warn("{} is set but the host has no master keyring{}", SETTING_KEY, OFF);
      return Optional.empty();
    }
    SecretSealer sealer = new SecretSealer(keyring);
    if (!sealer.isSealed(stored)) {
      log.warn("{} is not in the sealed form, a value in clear is never used{}", SETTING_KEY, OFF);
      return Optional.empty();
    }
    byte[] pepper;
    try {
      pepper = Base64.getUrlDecoder().decode(sealer.open(SCOPE, stored));
    } catch (IllegalStateException | IllegalArgumentException unopened) {
      // The class only: a message could carry a part of what was read.
      log.warn(
          "{} does not open for its scope ({}){}",
          SETTING_KEY,
          unopened.getClass().getSimpleName(),
          OFF);
      return Optional.empty();
    }
    if (pepper.length < MIN_BYTES) {
      log.warn("{} is shorter than {} bytes{}", SETTING_KEY, MIN_BYTES, OFF);
      return Optional.empty();
    }
    return Optional.of(pepper);
  }

  @Override
  public String toString() {
    return "SealedCheckSubjectPepperAdapter[" + SETTING_KEY + "]";
  }
}
