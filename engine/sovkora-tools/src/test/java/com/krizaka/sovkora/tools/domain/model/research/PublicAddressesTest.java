package com.krizaka.sovkora.tools.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The SSRF guard refuses every inside address, in IPv4, IPv6 and their mapped forms. */
class PublicAddressesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "0.0.0.0",
        "10.1.2.3",
        "172.16.0.1",
        "172.31.255.255",
        "192.168.1.1",
        "169.254.169.254",
        "100.64.0.1",
        "198.18.0.1",
        "192.0.2.1",
        "203.0.113.5",
        "224.0.0.1",
        "255.255.255.255",
        "::1",
        "::",
        "fc00::1",
        "fd12:3456::1",
        "fe80::1",
        "ff02::1",
        "2001:db8::1",
        "::ffff:127.0.0.1",
        "::ffff:10.0.0.1",
        "::ffff:169.254.169.254",
        "64:ff9b::a00:1",
        "64:ff9b::7f00:1"
      })
  void refusesTheInside(String literal) throws Exception {
    assertThat(PublicAddresses.isPublic(InetAddress.getByName(literal))).as(literal).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"1.1.1.1", "8.8.8.8", "172.32.0.1", "2606:4700:4700::1111", "::ffff:1.1.1.1"})
  void acceptsPublicAddresses(String literal) throws Exception {
    assertThat(PublicAddresses.isPublic(InetAddress.getByName(literal))).as(literal).isTrue();
  }
}
