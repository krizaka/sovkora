package com.krizaka.sovkora.tools.domain.port;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Resolves a host name, once per hop: the addresses it returns are the ones checked and used. */
public interface HostResolver {

  /**
   * The addresses of a host.
   *
   * @param host the host name or literal
   * @return its addresses
   * @throws UnknownHostException when it does not resolve
   */
  List<InetAddress> resolve(String host) throws UnknownHostException;
}
