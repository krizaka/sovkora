package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthorityTest {

  @Test
  void validAuthority() {
    var auth = new Authority("ROLE_USER");
    assertEquals("ROLE_USER", auth.name());
  }

  @Test
  void nullName_throws() {
    assertThrows(IllegalArgumentException.class, () -> new Authority(null));
  }

  @Test
  void blankName_throws() {
    assertThrows(IllegalArgumentException.class, () -> new Authority("  "));
  }

  @Test
  void emptyName_throws() {
    assertThrows(IllegalArgumentException.class, () -> new Authority(""));
  }

  @Test
  void equality() {
    assertEquals(new Authority("ROLE_ADMIN"), new Authority("ROLE_ADMIN"));
    assertNotEquals(new Authority("ROLE_ADMIN"), new Authority("ROLE_USER"));
  }
}
