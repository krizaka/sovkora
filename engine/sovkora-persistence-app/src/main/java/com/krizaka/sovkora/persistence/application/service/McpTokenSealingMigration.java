package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seals the MCP tokens that were stored in clear before sovkora-engine#58, once, at startup.
 *
 * <p>The sealing needs the master keyring, which only the application holds, so this cannot be a
 * SQL migration. It is idempotent: a sealed value is left alone, and a database with nothing in
 * clear costs one read. Without a keyring it logs and leaves the rows as they are — taking the
 * router down would not make the tokens safer — and every new write still refuses to store clear
 * text.
 */
@Component
class McpTokenSealingMigration implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(McpTokenSealingMigration.class);

  private final UserMcpServerRepository users;
  private final PlatformMcpServerRepository platform;
  private final McpTokenSealer tokens;

  McpTokenSealingMigration(
      UserMcpServerRepository users, PlatformMcpServerRepository platform, McpTokenSealer tokens) {
    this.users = Objects.requireNonNull(users, "UserMcpServerRepository cannot be null");
    this.platform = Objects.requireNonNull(platform, "PlatformMcpServerRepository cannot be null");
    this.tokens = Objects.requireNonNull(tokens, "McpTokenSealer cannot be null");
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    try {
      int sealed = sealUsers() + sealPlatform();
      if (sealed > 0) {
        logger.info("Sealed {} MCP server token(s) that were stored in clear", sealed);
      }
    } catch (IllegalStateException noKeyring) {
      logger.error(
          "MCP server tokens stored in clear could not be sealed: {}", noKeyring.getMessage());
    }
  }

  int sealUsers() {
    int count = 0;
    for (UserMcpServerEntity row : users.findAll()) {
      String stored = row.getAuthToken();
      if (stored != null && !stored.isBlank() && !tokens.isSealed(stored)) {
        row.setAuthToken(tokens.seal(row.getUserId(), stored));
        users.save(row);
        count++;
      }
    }
    return count;
  }

  int sealPlatform() {
    int count = 0;
    for (PlatformMcpServerEntity row : platform.findAll()) {
      String stored = row.getAuthToken();
      if (stored != null && !stored.isBlank() && !tokens.isSealed(stored)) {
        row.setAuthToken(tokens.seal(null, stored));
        platform.save(row);
        count++;
      }
    }
    return count;
  }
}
