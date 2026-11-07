package com.krizaka.sovkora.engine.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.engine.tools.infrastructure.adapter.persistence.entity.ResearchFetchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** The research fetch audit. */
public interface ResearchFetchRepository extends JpaRepository<ResearchFetchEntity, Long> {}
