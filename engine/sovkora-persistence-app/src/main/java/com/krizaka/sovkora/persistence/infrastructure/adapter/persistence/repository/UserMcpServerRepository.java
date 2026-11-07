package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for performs database operations on {@link UserMcpServerEntity}. */
public interface UserMcpServerRepository extends JpaRepository<UserMcpServerEntity, Integer> {

  List<UserMcpServerEntity> findByUserIdAndEnabledTrue(String userId);

  List<UserMcpServerEntity> findByUserId(String userId);
}
