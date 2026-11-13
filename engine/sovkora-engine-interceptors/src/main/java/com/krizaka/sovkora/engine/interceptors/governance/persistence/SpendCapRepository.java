package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository of the provider-spend caps. */
public interface SpendCapRepository extends JpaRepository<SpendCapEntity, UUID> {

  Optional<SpendCapEntity> findByEnvironmentAndProductKey(String environment, String productKey);

  List<SpendCapEntity> findAllByOrderByEnvironmentAscProductKeyAsc();
}
