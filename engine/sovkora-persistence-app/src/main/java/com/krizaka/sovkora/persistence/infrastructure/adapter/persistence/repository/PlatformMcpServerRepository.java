package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for performs database operations on {@link PlatformMcpServerEntity}.
 */
public interface PlatformMcpServerRepository
    extends JpaRepository<PlatformMcpServerEntity, Integer> {

  List<PlatformMcpServerEntity> findByEnabledTrue();
}
