package com.krizaka.sovkora.tools.domain.model.research;

/**
 * Why a research fetch was not made, or not kept (ADR-087 §4). Each value is written to the audit
 * ({@code research_fetch.refused_reason}); none of them carries the URL's path or query.
 */
public enum FetchRefusal {
  /** Research is switched off, or the source is disabled. */
  DISABLED,
  /** The scheme is not https (or http where the source allows it). */
  SCHEME,
  /** The host resolves to a non-public address: loopback, private, link-local, metadata… */
  SSRF,
  /** The host and path are outside the source's allow patterns, or inside its deny patterns. */
  ALLOWLIST,
  /** robots.txt disallows the path for our user agent. */
  ROBOTS,
  /** The source's or the host's rate is spent. */
  RATE,
  /** The body is larger than {@code research.fetch.max-bytes}. */
  SIZE,
  /** The connection or the read took longer than {@code research.fetch.timeout-ms}. */
  TIMEOUT,
  /** The content type is not one research reads. */
  CONTENT_TYPE,
  /**
   * More redirect hops than {@code research.fetch.max-redirects}, or a redirect without a target.
   */
  REDIRECTS,
  /** The server answered an error status, or the connection failed. */
  HTTP_ERROR
}
