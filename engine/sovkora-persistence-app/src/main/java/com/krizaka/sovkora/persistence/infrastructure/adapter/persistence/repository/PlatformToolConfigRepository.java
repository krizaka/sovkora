package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformToolConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for performs database operations on {@link PlatformToolConfigEntity}.
 */
public interface PlatformToolConfigRepository
    extends JpaRepository<PlatformToolConfigEntity, Integer> {

  Optional<PlatformToolConfigEntity> findByToolId(String toolId);
}
