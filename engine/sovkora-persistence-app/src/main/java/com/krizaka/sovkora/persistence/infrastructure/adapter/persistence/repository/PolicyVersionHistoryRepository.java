package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PolicyVersionHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for policy version history — supports rollback queries. */
public interface PolicyVersionHistoryRepository
    extends JpaRepository<PolicyVersionHistoryEntity, UUID> {

  /** Find the most recent version snapshot for a given policy. */
  Optional<PolicyVersionHistoryEntity> findTopByPolicyIdOrderByCreatedAtDesc(UUID policyId);
}
