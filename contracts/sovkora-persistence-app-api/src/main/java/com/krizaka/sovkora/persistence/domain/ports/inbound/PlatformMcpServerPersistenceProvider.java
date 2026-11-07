package com.krizaka.sovkora.persistence.domain.ports.inbound;

import com.krizaka.sovkora.persistence.domain.model.PlatformMcpServerDto;
import java.util.List;
import java.util.Optional;

/** Port interface for managing PlatformMcpServer persistence operations. */
public interface PlatformMcpServerPersistenceProvider {

  List<PlatformMcpServerDto> findByEnabledTrue();

  List<PlatformMcpServerDto> findAll();

  Optional<PlatformMcpServerDto> findById(Integer id);

  PlatformMcpServerDto save(PlatformMcpServerDto serverDto);

  void deleteById(Integer id);
}
