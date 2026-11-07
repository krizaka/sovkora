package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.sovkora.crypto.SecretSealer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The platform keys of the check providers, sealed in the admin settings (sovkora-engine#73): the
 * setting {@code checks.provider.<name>.key} holds the key sealed with the platform keyring for the
 * scope of that provider ({@link SecretSealer}, ADR-054), never an environment variable. A value in
 * clear, one that does not open, or a host with no keyring is no key: the check fails closed
 * ({@code NO_KEY}). A warning names the setting and the reason, never a value.
 *
 * <p>A user's own key has no store yet: {@link #userKey} answers empty until the user vault lands.
 */
@Service
class SealedCheckKeyStoreAdapter implements CheckKeyStore {

  private static final Logger log = LoggerFactory.getLogger(SealedCheckKeyStoreAdapter.class);

  private final RuntimeConfigProvider settings;
  private final ObjectProvider<MasterKeyProvider> keys;

  SealedCheckKeyStoreAdapter(
      RuntimeConfigProvider settings, ObjectProvider<MasterKeyProvider> keys) {
    this.settings = Objects.requireNonNull(settings, "RuntimeConfigProvider cannot be null");
    this.keys = Objects.requireNonNull(keys, "MasterKeyProvider provider cannot be null");
  }

  /** The setting that holds a provider's sealed platform key. */
  static String settingKey(String provider) {
    return "checks.provider." + provider + ".key";
  }

  /** The owner a provider's key is sealed for: a key sealed for another provider does not open. */
  static UUID scope(String provider) {
    return UUID.nameUUIDFromBytes(
        ("sovkora:platform:" + settingKey(provider)).getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Optional<String> userKey(String userId, String provider) {
    return Optional.empty();
  }

  @Override
  public Optional<String> platformKey(String provider) {
    String setting = settingKey(provider);
    String stored = settings.getString(setting, null);
    if (stored == null || stored.isBlank()) {
      return Optional.empty();
    }
    MasterKeyProvider keyring = keys.getIfAvailable();
    if (keyring == null) {
      log.warn("{} is set but the host has no master keyring", setting);
      return Optional.empty();
    }
    SecretSealer sealer = new SecretSealer(keyring);
    if (!sealer.isSealed(stored)) {
      log.warn("{} is not in the sealed form, a value in clear is never used", setting);
      return Optional.empty();
    }
    try {
      String key = sealer.open(scope(provider), stored);
      return key.isBlank() ? Optional.empty() : Optional.of(key);
    } catch (IllegalStateException | IllegalArgumentException unopened) {
      log.warn("{} does not open for its scope ({})", setting, unopened.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return "SealedCheckKeyStoreAdapter";
  }
}
