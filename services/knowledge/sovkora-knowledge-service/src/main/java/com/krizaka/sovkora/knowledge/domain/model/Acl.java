package com.krizaka.sovkora.knowledge.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The access rule of a source and of every passage indexed from it (ADR-091): one tenant, an owner,
 * and the principals ({@code role:…}, {@code group:…}, {@code user:…}) allowed to read it.
 *
 * <p>The same rule is written twice and must stay the same: as SQL in {@code
 * KnowledgeQueryService}, and as a vector-store filter in {@code VectorAccess}. Both are applied
 * <em>inside</em> the query, before ranking — a passage the caller cannot read is never fetched, so
 * it cannot leak through a snippet, a count or a cache.
 */
public final class Acl {

  /** Metadata keys written on every indexed passage. */
  public static final String TENANT = "tenant_id";

  public static final String OWNER = "owner_id";
  public static final String SOURCE = "source_id";
  public static final String TOOL = "tool_id";

  private static final Pattern ENTRY = Pattern.compile("(role|group|user):[A-Za-z0-9_.@-]{1,100}");

  private Acl() {}

  public static String role(String role) {
    return "role:" + role;
  }

  public static String group(String group) {
    return "group:" + group;
  }

  public static String user(String user) {
    return "user:" + user;
  }

  /** Whether {@code entry} is a well-formed ACL entry. */
  public static boolean isEntry(String entry) {
    return entry != null && ENTRY.matcher(entry).matches();
  }

  /**
   * The passage-metadata key that records one ACL entry. A hash, not the entry: a metadata key is
   * part of a JSON path in the store, and two different entries must never share a key.
   */
  public static String key(String entry) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(entry.getBytes(StandardCharsets.UTF_8));
      return "acl_" + HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /** What the store must be given for a passage: tenant, owner, source and one flag per entry. */
  public static Map<String, Object> metadata(
      String tool, long source, String tenant, String owner, List<String> entries) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(TOOL, tool);
    metadata.put(SOURCE, Long.toString(source));
    metadata.put(TENANT, tenant);
    if (owner != null) {
      metadata.put(OWNER, owner);
    }
    entries.stream().filter(Acl::isEntry).forEach(entry -> metadata.put(key(entry), Boolean.TRUE));
    return metadata;
  }
}
