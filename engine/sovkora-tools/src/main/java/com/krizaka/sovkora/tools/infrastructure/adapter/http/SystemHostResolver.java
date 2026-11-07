package com.krizaka.sovkora.tools.infrastructure.adapter.http;

import com.krizaka.sovkora.tools.domain.port.HostResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.stereotype.Component;

/** The JVM's resolver, called once per hop by {@link PinnedHttpClient}. */
@Component
class SystemHostResolver implements HostResolver {

  @Override
  public List<InetAddress> resolve(String host) throws UnknownHostException {
    return List.of(InetAddress.getAllByName(host));
  }
}
