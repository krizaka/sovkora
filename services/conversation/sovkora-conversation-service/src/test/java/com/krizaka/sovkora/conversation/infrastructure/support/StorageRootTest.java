package com.krizaka.sovkora.conversation.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageRootTest {

  @TempDir Path dir;

  @Test
  @DisplayName("An absolute configured root is the root, as configured")
  void absoluteRootIsKept() {
    assertThat(StorageRoot.resolve(dir.toString())).isEqualTo(dir.toAbsolutePath().normalize());
  }

  @Test
  @DisplayName("A missing or blank root fails clearly, naming the setting")
  void missingRootFails() {
    assertThatThrownBy(() -> StorageRoot.resolve(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UPLOAD_DIR");
    assertThatThrownBy(() -> StorageRoot.resolve("  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UPLOAD_DIR");
  }

  @Test
  @DisplayName(
      "A relative root is never anchored on an AGENTS.md found above the working directory")
  void noAgentsMdWalk() throws Exception {
    Path cwd = Path.of("").toAbsolutePath();
    // an AGENTS.md exists above this module (the repo root), and must make no difference
    boolean agentsAbove = false;
    for (Path p = cwd; p != null; p = p.getParent()) {
      agentsAbove |= Files.exists(p.resolve("AGENTS.md")) && !p.equals(cwd);
    }
    assertThat(agentsAbove).isTrue();
    assertThat(StorageRoot.resolve("var/uploads"))
        .isEqualTo(cwd.resolve("var/uploads").normalize());
  }
}
