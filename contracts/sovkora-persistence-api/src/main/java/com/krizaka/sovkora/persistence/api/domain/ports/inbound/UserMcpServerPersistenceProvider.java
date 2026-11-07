package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.UserMcpServerDto;
import java.util.List;
import java.util.Optional;

/** Port interface for managing UserMcpServer persistence operations. */
public interface UserMcpServerPersistenceProvider {

  List<UserMcpServerDto> findByUserIdAndEnabledTrue(String userId);

  List<UserMcpServerDto> findByUserId(String userId);

  Optional<UserMcpServerDto> findById(Integer id);

  UserMcpServerDto save(UserMcpServerDto serverDto);

  void deleteById(Integer id);
}
