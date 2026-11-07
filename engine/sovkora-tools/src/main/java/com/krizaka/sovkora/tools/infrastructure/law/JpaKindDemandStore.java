package com.krizaka.sovkora.tools.infrastructure.law;

import com.krizaka.sovkora.tools.application.port.law.KindDemandStore;
import com.krizaka.sovkora.tools.domain.model.law.KindDemand;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.KindDemandRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link KindDemandStore} on the job-service's {@code kind_demand} table (ADR-083 data source). */
@Component
class JpaKindDemandStore implements KindDemandStore {

  private final KindDemandRepository demand;

  JpaKindDemandStore(KindDemandRepository demand) {
    this.demand = Objects.requireNonNull(demand, "KindDemandRepository");
  }

  @Override
  @Transactional(readOnly = true)
  public List<KindDemand> list() {
    return demand.mostAsked().stream()
        .map(
            row ->
                new KindDemand(
                    row.getId().getJurisdiction(),
                    row.getId().getKindId(),
                    row.getCount(),
                    row.getFirstSeen(),
                    row.getLastSeen()))
        .toList();
  }
}
