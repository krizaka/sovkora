package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.RuntimeConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link RuntimeConfigEntity} (keyed by {@code config_key}). */
@Repository
public interface RuntimeConfigRepository extends JpaRepository<RuntimeConfigEntity, String> {}
