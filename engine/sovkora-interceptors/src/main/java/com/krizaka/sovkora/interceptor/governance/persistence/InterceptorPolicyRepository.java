package com.krizaka.sovkora.interceptor.governance.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for interceptor policy entities. */
public interface InterceptorPolicyRepository extends JpaRepository<InterceptorPolicyEntity, UUID> {

  List<InterceptorPolicyEntity> findAllByOrderByExecutionOrderAsc();
}
