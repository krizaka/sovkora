package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * The installation tokens of a GitHub App (the org's {@code krizaka-agents}), never a personal
 * token: the App signs a short JWT with its private key, GitHub answers a token valid one hour for
 * the installation, and it is renewed before it ends. The key is configuration, never code, and the
 * token is never logged.
 */
public final class GitHubAppInstallationTokens {

  /** A token is renewed this long before GitHub says it ends. */
  private static final Duration RENEW_BEFORE = Duration.ofMinutes(5);

  /** GitHub accepts an App JWT of at most ten minutes; it is dated a minute back for clock skew. */
  private static final Duration JWT_LIFE = Duration.ofMinutes(9);

  private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

  private static final Charset UTF8 = StandardCharsets.UTF_8;

  private final RestClient github;
  private final String appId;
  private final long installationId;
  private final PrivateKey key;
  private final Clock clock;

  private String token;
  private Instant expiresAt = Instant.MIN;

  /**
   * Creates the token source.
   *
   * @param github the GitHub API client, without credentials
   * @param appId the App id
   * @param installationId the App's installation on the organisation
   * @param key the App's private key
   * @param clock the clock (injected)
   */
  public GitHubAppInstallationTokens(
      RestClient github, String appId, long installationId, PrivateKey key, Clock clock) {
    this.github = Objects.requireNonNull(github, "github");
    this.appId = requireText(appId, "appId");
    if (installationId < 1) {
      throw new IllegalArgumentException("the installation id is required");
    }
    this.installationId = installationId;
    this.key = Objects.requireNonNull(key, "key");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The token source of an App on a GitHub API, with the system clock.
   *
   * @param apiUrl the GitHub API, such as {@code https://api.github.com}
   * @param userAgent who calls, as GitHub asks every client to say
   * @param appId the App id
   * @param installationId the App's installation on the organisation, required
   * @param privateKeyPem the App's private key in PEM
   * @return the token source
   */
  public static GitHubAppInstallationTokens forApp(
      String apiUrl, String userAgent, String appId, Long installationId, String privateKeyPem) {
    if (installationId == null) {
      throw new IllegalArgumentException("the installation id is required");
    }
    RestClient github =
        RestClient.builder()
            .baseUrl(requireText(apiUrl, "the GitHub API"))
            .defaultHeader(HttpHeaders.USER_AGENT, requireText(userAgent, "the user agent"))
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    return new GitHubAppInstallationTokens(
        github, appId, installationId, privateKey(privateKeyPem), Clock.systemUTC());
  }

  /**
   * A client of the same API that sends the installation token on every request.
   *
   * @return the authenticated client
   */
  public RestClient client() {
    return github.mutate().requestInterceptor(interceptor()).build();
  }

  /**
   * A valid installation token, minted when the last one is about to end.
   *
   * @return the token
   */
  public synchronized String token() {
    Instant now = clock.instant();
    if (token == null || !now.isBefore(expiresAt.minus(RENEW_BEFORE))) {
      Map<?, ?> answer =
          github
              .post()
              .uri("/app/installations/{id}/access_tokens", installationId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(appId, key, now))
              .retrieve()
              .body(Map.class);
      if (answer == null || !(answer.get("token") instanceof String minted)) {
        throw new IllegalStateException("GitHub answered no installation token");
      }
      token = minted;
      expiresAt = Instant.parse(String.valueOf(answer.get("expires_at")));
    }
    return token;
  }

  /**
   * Authenticates every request of a client with the installation token.
   *
   * @return the interceptor
   */
  public ClientHttpRequestInterceptor interceptor() {
    return (request, body, execution) -> {
      request.getHeaders().setBearerAuth(token());
      return execution.execute(request, body);
    };
  }

  /**
   * The App's JWT (RS256), as GitHub asks for it.
   *
   * @param appId the App id, the issuer
   * @param key the App's private key
   * @param now the instant
   * @return the signed token
   */
  static String jwt(String appId, PrivateKey key, Instant now) {
    Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    String header = url.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(UTF8));
    long issued = now.minus(CLOCK_SKEW).getEpochSecond();
    long expires = now.plus(JWT_LIFE).getEpochSecond();
    String payload =
        url.encodeToString(
            ("{\"iat\":" + issued + ",\"exp\":" + expires + ",\"iss\":\"" + appId + "\"}")
                .getBytes(UTF8));
    String signing = header + "." + payload;
    try {
      Signature rsa = Signature.getInstance("SHA256withRSA");
      rsa.initSign(key);
      rsa.update(signing.getBytes(UTF8));
      return signing + "." + url.encodeToString(rsa.sign());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot sign the GitHub App token", e);
    }
  }

  /**
   * Reads the App's private key, as GitHub gives it (PKCS#1, {@code BEGIN RSA PRIVATE KEY}) or as
   * PKCS#8 ({@code BEGIN PRIVATE KEY}).
   *
   * @param pem the key in PEM
   * @return the key
   * @throws IllegalArgumentException when it is not an RSA private key in PEM
   */
  public static PrivateKey privateKey(String pem) {
    requireText(pem, "the GitHub App private key");
    boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
    String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
    try {
      byte[] der = Base64.getDecoder().decode(body);
      byte[] pkcs8 = pkcs1 ? wrapPkcs1(der) : der;
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      throw new IllegalArgumentException("the GitHub App private key is not an RSA key in PEM", e);
    }
  }

  /** The rsaEncryption algorithm identifier: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }. */
  private static final byte[] RSA_ALGORITHM = {
    0x30,
    0x0d,
    0x06,
    0x09,
    0x2a,
    (byte) 0x86,
    0x48,
    (byte) 0x86,
    (byte) 0xf7,
    0x0d,
    0x01,
    0x01,
    0x01,
    0x05,
    0x00
  };

  /** PKCS#8 PrivateKeyInfo around a PKCS#1 RSAPrivateKey. */
  private static byte[] wrapPkcs1(byte[] pkcs1) {
    byte[] version = {0x02, 0x01, 0x00};
    byte[] octets = tagged(0x04, pkcs1);
    byte[] content = new byte[version.length + RSA_ALGORITHM.length + octets.length];
    System.arraycopy(version, 0, content, 0, version.length);
    System.arraycopy(RSA_ALGORITHM, 0, content, version.length, RSA_ALGORITHM.length);
    System.arraycopy(octets, 0, content, version.length + RSA_ALGORITHM.length, octets.length);
    return tagged(0x30, content);
  }

  private static byte[] tagged(int tag, byte[] content) {
    byte[] length = derLength(content.length);
    byte[] out = new byte[1 + length.length + content.length];
    out[0] = (byte) tag;
    System.arraycopy(length, 0, out, 1, length.length);
    System.arraycopy(content, 0, out, 1 + length.length, content.length);
    return out;
  }

  private static byte[] derLength(int length) {
    if (length < 0x80) {
      return new byte[] {(byte) length};
    }
    int bytes = length > 0xffffff ? 4 : length > 0xffff ? 3 : length > 0xff ? 2 : 1;
    byte[] out = new byte[1 + bytes];
    out[0] = (byte) (0x80 | bytes);
    for (int i = bytes; i >= 1; i--) {
      out[i] = (byte) (length & 0xff);
      length >>= 8;
    }
    return out;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.trim();
  }
}
