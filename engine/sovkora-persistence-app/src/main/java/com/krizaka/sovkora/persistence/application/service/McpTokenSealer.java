package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.crypto.SecretSealer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Seals the bearer tokens of MCP servers before they reach the table (sovkora-engine#58).
 *
 * <p>The token a user gives for their MCP server — or an operator for a platform server — was
 * stored in {@code auth_token} in clear: anyone who could read the table could call that server as
 * its owner. It is sealed now with the platform's master keyring, the mechanism of the asset store
 * and of the automation service's trigger secrets ({@link SecretSealer}, ADR-054), not a new one.
 *
 * <p>A token is sealed for its owner (the user's id, or one platform scope), so a sealed value
 * copied to another user's row does not open. A value that is not in the sealed form is a row
 * written before this change: {@link #open} returns it as it is, and {@link
 * McpTokenSealingMigration} seals those rows at startup, so that tolerance lasts one boot.
 *
 * <p>Without a keyring no token can be stored: a write fails, it never falls back to clear text.
 */
@Component
class McpTokenSealer {

  /** The one owner of every platform server's token. */
  static final UUID PLATFORM_SCOPE =
      UUID.nameUUIDFromBytes("sovkora:platform-mcp-servers".getBytes(StandardCharsets.UTF_8));

  private final ObjectProvider<MasterKeyProvider> keys;
  private volatile SecretSealer sealer;

  McpTokenSealer(ObjectProvider<MasterKeyProvider> keys) {
    this.keys = keys;
  }

  /**
   * The token sealed for its owner, or {@code null} for no token.
   *
   * <p>Always sealed, even when it looks sealed: what a caller saves is a token in clear, and one
   * shaped like {@code v1.a.b.c.d} must not reach the table unencrypted (and then fail to open).
   * Whether a <i>stored</i> value is already sealed is the migration's question, not this one's.
   *
   * @param owner the user id, or {@code null} for a platform server
   * @param token the token in clear
   * @return the sealed form
   */
  String seal(String owner, String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    return sealer().seal(scope(owner), token);
  }

  /**
   * The token in clear, for the MCP client only.
   *
   * @param owner the user id, or {@code null} for a platform server
   * @param stored what the table holds
   * @return the token, or {@code null} for none
   */
  String open(String owner, String stored) {
    if (stored == null || stored.isBlank()) {
      return null;
    }
    if (!isSealed(stored)) {
      return stored; // written before #58; sealed at the next startup by McpTokenSealingMigration
    }
    return sealer().open(scope(owner), stored);
  }

  /**
   * Whether a stored value is already sealed.
   *
   * @param stored what the table holds
   * @return true when it is in the sealed form
   */
  boolean isSealed(String stored) {
    return stored != null && stored.startsWith("v1.") && stored.split("\\.", -1).length == 5;
  }

  private SecretSealer sealer() {
    SecretSealer s = sealer;
    if (s == null) {
      MasterKeyProvider provider = keys.getIfAvailable();
      if (provider == null) {
        throw new IllegalStateException(
            "no master keyring: an MCP server token cannot be stored or read (ADR-054)");
      }
      s = new SecretSealer(provider);
      sealer = s;
    }
    return s;
  }

  static UUID scope(String owner) {
    if (owner == null) {
      return PLATFORM_SCOPE;
    }
    try {
      return UUID.fromString(owner);
    } catch (IllegalArgumentException notAUuid) {
      return UUID.nameUUIDFromBytes(("sovkora:user:" + owner).getBytes(StandardCharsets.UTF_8));
    }
  }
}
