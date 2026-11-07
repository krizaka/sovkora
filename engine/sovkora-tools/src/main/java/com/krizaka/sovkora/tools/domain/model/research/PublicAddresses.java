package com.krizaka.sovkora.tools.domain.model.research;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * The SSRF guard's one question: may research connect to this address? Only to a public unicast one
 * (ADR-087 §4). Refused: loopback, unspecified, private (RFC 1918), link-local (and the cloud
 * metadata endpoint inside it), CGNAT (100.64/10), benchmarking (198.18/15), documentation,
 * multicast and reserved IPv4; IPv6 loopback, ULA (fc00::/7), link-local, site-local, multicast,
 * documentation, and IPv4-mapped or NAT64 forms of any refused IPv4.
 */
public final class PublicAddresses {

  private PublicAddresses() {}

  /**
   * Whether research may connect to the address.
   *
   * @param address the resolved address
   * @return true for a public unicast address
   */
  public static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    byte[] b = address.getAddress();
    if (address instanceof Inet4Address) {
      return isPublicV4(b);
    }
    if (address instanceof Inet6Address) {
      int first = b[0] & 0xff;
      if ((first & 0xfe) == 0xfc) {
        return false; // ULA fc00::/7
      }
      if (first == 0x20
          && (b[1] & 0xff) == 0x01
          && (b[2] & 0xff) == 0x0d
          && (b[3] & 0xff) == 0xb8) {
        return false; // documentation 2001:db8::/32
      }
      if (isMappedOrTranslated(b)) {
        return isPublicV4(new byte[] {b[12], b[13], b[14], b[15]});
      }
      return true;
    }
    return false;
  }

  private static boolean isMappedOrTranslated(byte[] b) {
    boolean zeros80 = true;
    for (int i = 0; i < 10; i++) {
      zeros80 &= b[i] == 0;
    }
    boolean mapped = zeros80 && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    boolean compatible = zeros80 && b[10] == 0 && b[11] == 0;
    // NAT64 well-known prefix 64:ff9b::/96
    boolean nat64 =
        b[0] == 0 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
    return mapped || compatible || nat64;
  }

  private static boolean isPublicV4(byte[] b) {
    int a = b[0] & 0xff;
    int c = b[1] & 0xff;
    if (a == 0 || a == 10 || a == 127 || a >= 224) {
      return false; // this network, private, loopback, multicast and reserved
    }
    if (a == 100 && c >= 64 && c <= 127) {
      return false; // CGNAT 100.64.0.0/10
    }
    if (a == 169 && c == 254) {
      return false; // link-local, including 169.254.169.254
    }
    if (a == 172 && c >= 16 && c <= 31) {
      return false;
    }
    if (a == 192 && c == 168) {
      return false;
    }
    if (a == 192 && c == 0 && ((b[2] & 0xff) == 0 || (b[2] & 0xff) == 2)) {
      return false; // IETF assignments 192.0.0/24, documentation 192.0.2/24
    }
    if (a == 198 && (c == 18 || c == 19)) {
      return false; // benchmarking
    }
    if (a == 198 && c == 51 && (b[2] & 0xff) == 100) {
      return false; // documentation
    }
    return !(a == 203 && c == 0 && (b[2] & 0xff) == 113); // documentation
  }
}
