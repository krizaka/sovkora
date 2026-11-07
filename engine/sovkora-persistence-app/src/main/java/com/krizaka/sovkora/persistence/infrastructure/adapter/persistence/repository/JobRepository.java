package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.JobEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link JobEntity}. */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, String> {
  Page<JobEntity> findByUserId(String userId, Pageable pageable);

  /** The owner's jobs but the ones they deleted. */
  Page<JobEntity> findByUserIdAndStatusNot(String userId, String status, Pageable pageable);

  /** Every job but the deleted ones. */
  Page<JobEntity> findByStatusNot(String status, Pageable pageable);

  List<JobEntity> findByStatusIn(Collection<String> statuses);

  void deleteByUserId(String userId);
}
