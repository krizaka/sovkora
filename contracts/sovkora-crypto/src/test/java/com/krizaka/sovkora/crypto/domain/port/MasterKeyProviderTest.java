package com.krizaka.sovkora.crypto.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.crypto.domain.port.MasterKeyProvider.WrappedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A wrapped key is a value: its id and its bytes, compared by content, never printed. */
class MasterKeyProviderTest {

  private static final byte[] BLOB = {1, 2, 3, 4, 5};

  @Test
  @DisplayName("two wrapped keys with the same id and bytes are equal and hash alike")
  void equalByContent() {
    WrappedKey one = new WrappedKey("k1", BLOB.clone());
    WrappedKey same = new WrappedKey("k1", BLOB.clone());

    assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
  }

  @Test
  @DisplayName("another id or other bytes make another key")
  void differsByIdOrBytes() {
    WrappedKey one = new WrappedKey("k1", BLOB.clone());

    assertThat(one)
        .isNotEqualTo(new WrappedKey("k2", BLOB.clone()))
        .isNotEqualTo(new WrappedKey("k1", new byte[] {1, 2, 3, 4, 6}))
        .isNotEqualTo(null)
        .isNotEqualTo("k1");
  }

  @Test
  @DisplayName("the text form names the key and the length, never the bytes")
  void textFormHidesTheBytes() {
    String text = new WrappedKey("k1", new byte[] {42, 43, 44}).toString();

    assertThat(text).contains("k1").contains("3 bytes").doesNotContain("42").doesNotContain("[B@");
  }

  @Test
  @DisplayName("the bytes are copied in and out")
  void bytesAreCopied() {
    byte[] given = BLOB.clone();
    WrappedKey key = new WrappedKey("k1", given);
    given[0] = 9;
    key.wrapped()[1] = 9;

    assertThat(key.wrapped()).isEqualTo(BLOB);
  }

  @Test
  @DisplayName("an id and some bytes are required")
  void refusesMissingParts() {
    assertThatThrownBy(() -> new WrappedKey(" ", BLOB))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WrappedKey("k1", new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
