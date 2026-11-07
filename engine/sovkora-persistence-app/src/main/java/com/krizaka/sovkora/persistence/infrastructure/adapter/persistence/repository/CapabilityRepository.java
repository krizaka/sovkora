package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CapabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link CapabilityEntity}. */
@Repository
public interface CapabilityRepository extends JpaRepository<CapabilityEntity, String> {}
