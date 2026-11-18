package com.krizaka.sovkora.engine.core.domain.ports.outbound;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: which MCP servers the engine may open. Asked when a server is registered and again
 * each time one is opened, so a row written before this check, or a host whose name now resolves
 * elsewhere, is refused at use as well.
 *
 * <p>A remote server is reached over https on public addresses only. A local (stdio) server is not
 * a command anyone may type: it is an id the deployment declares in its configuration, with the
 * command and the arguments it launches. With no declaration, no process is ever started.
 */
public interface McpServerPolicy {

  /** Why a remote MCP endpoint is refused. None of them carries the URL. */
  enum Refusal {
    /** The URL does not parse, or names no host. */
    MALFORMED,
    /** The scheme is not https. */
    SCHEME,
    /** The host does not resolve. */
    UNRESOLVED,
    /** The host resolves to an address that is not public: loopback, private, link-local… */
    ADDRESS
  }

  /**
   * A local MCP server as the deployment declares it.
   *
   * @param command the executable
   * @param args its arguments, in order
   */
  record StdioLaunch(String command, List<String> args) {

    /** Validates the declaration. */
    public StdioLaunch {
      if (command == null || command.isBlank()) {
        throw new IllegalArgumentException("a stdio MCP server declares its command");
      }
      args = args == null ? List.of() : List.copyOf(args);
    }
  }

  /**
   * Whether a remote MCP endpoint may be reached.
   *
   * @param url the endpoint as registered
   * @return the reason it is refused, or empty when it may be reached
   */
  Optional<Refusal> refusal(String url);

  /**
   * The local MCP server declared under this id.
   *
   * @param id the id a platform server names
   * @return the declaration, or empty when the deployment declares none under this id
   */
  Optional<StdioLaunch> stdioLaunch(String id);

  /**
   * The policy of an engine that has none wired: every remote endpoint is refused and no local
   * server is declared.
   *
   * @return the closed policy
   */
  static McpServerPolicy closed() {
    return Closed.INSTANCE;
  }

  /** The closed policy: every remote endpoint refused, no local server declared. */
  enum Closed implements McpServerPolicy {
    INSTANCE;

    @Override
    public Optional<Refusal> refusal(String url) {
      return Optional.of(Refusal.ADDRESS);
    }

    @Override
    public Optional<StdioLaunch> stdioLaunch(String id) {
      return Optional.empty();
    }
  }
}
