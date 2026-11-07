package com.krizaka.sovkora.tools.domain.model.law;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of a file, in lowercase hex: the fingerprint an import records. */
public final class Sha256 {

  private Sha256() {}

  /**
   * Hashes bytes.
   *
   * @param bytes the content
   * @return the 64 lowercase hexadecimal characters of the digest
   */
  public static String hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
    }
  }
}
