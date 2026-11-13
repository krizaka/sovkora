package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository of the provider price table. */
public interface ProviderPriceRepository extends JpaRepository<ProviderPriceEntity, String> {

  List<ProviderPriceEntity> findAllByOrderByModelKeyAsc();
}
