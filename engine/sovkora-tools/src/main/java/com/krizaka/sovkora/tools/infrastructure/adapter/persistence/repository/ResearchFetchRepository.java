package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchFetchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** The research fetch audit. */
public interface ResearchFetchRepository extends JpaRepository<ResearchFetchEntity, Long> {}
