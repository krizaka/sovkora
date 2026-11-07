package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.KindDemandEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.KindDemandId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code kind_demand}. */
public interface KindDemandRepository extends JpaRepository<KindDemandEntity, KindDemandId> {

  /**
   * Every row, the most asked first.
   *
   * @return the rows
   */
  @Query(
      "SELECT d FROM KindDemandEntity d ORDER BY d.count DESC, d.lastSeen DESC, d.id.jurisdiction,"
          + " d.id.kindId")
  List<KindDemandEntity> mostAsked();
}
