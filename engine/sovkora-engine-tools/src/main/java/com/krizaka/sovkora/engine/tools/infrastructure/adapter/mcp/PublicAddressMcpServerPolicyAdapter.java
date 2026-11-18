package com.krizaka.sovkora.engine.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy;
import com.krizaka.sovkora.engine.tools.domain.model.research.PublicAddresses;
import com.krizaka.sovkora.engine.tools.domain.port.HostResolver;
import com.krizaka.sovkora.engine.tools.infrastructure.config.McpProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The MCP server policy of the engine: a remote server over https whose host resolves to public
 * addresses only (the research guard, ADR-087 §4), and a local server only by an id the deployment
 * declares ({@link McpProperties}).
 */
@Component
public class PublicAddressMcpServerPolicyAdapter implements McpServerPolicy {

  private final HostResolver resolver;
  private final McpProperties properties;

  /**
   * Builds the policy.
   *
   * @param resolver the host resolver
   * @param properties the declared local servers
   */
  public PublicAddressMcpServerPolicyAdapter(HostResolver resolver, McpProperties properties) {
    this.resolver = resolver;
    this.properties = properties;
  }

  @Override
  public Optional<Refusal> refusal(String url) {
    URI uri;
    try {
      uri = new URI(url == null ? "" : url.trim());
    } catch (URISyntaxException malformed) {
      return Optional.of(Refusal.MALFORMED);
    }
    return shapeRefusal(uri).or(() -> addressRefusal(uri.getHost()));
  }

  /** An https URL that names a host, and no credentials in it. */
  private static Optional<Refusal> shapeRefusal(URI uri) {
    if (uri.getHost() == null || uri.getRawUserInfo() != null) {
      return Optional.of(Refusal.MALFORMED);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    return "https".equals(scheme) ? Optional.empty() : Optional.of(Refusal.SCHEME);
  }

  /** Every address the host resolves to is public. */
  private Optional<Refusal> addressRefusal(String host) {
    List<InetAddress> addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException unknown) {
      return Optional.of(Refusal.UNRESOLVED);
    }
    if (addresses.isEmpty()) {
      return Optional.of(Refusal.UNRESOLVED);
    }
    return addresses.stream().allMatch(PublicAddresses::isPublic)
        ? Optional.empty()
        : Optional.of(Refusal.ADDRESS);
  }

  @Override
  public Optional<StdioLaunch> stdioLaunch(String id) {
    return id == null ? Optional.empty() : Optional.ofNullable(properties.stdio().get(id));
  }
}
