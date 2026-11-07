package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolPermissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA Repository for the roles granted to each tool. */
@Repository
public interface ToolPermissionRepository extends JpaRepository<ToolPermissionEntity, Integer> {

  /**
   * The grants of one tool.
   *
   * @param toolId the tool's name
   * @return its rows, empty when an administrator set none
   */
  List<ToolPermissionEntity> findByToolId(String toolId);
}
