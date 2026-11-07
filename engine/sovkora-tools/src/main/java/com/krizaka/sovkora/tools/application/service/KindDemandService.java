package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.KindDemandStore;
import com.krizaka.sovkora.tools.domain.model.law.KindDemand;
import java.util.List;
import java.util.Objects;

/**
 * The Demand tab of the review queue (ADR-089 §6, §8): the unsupported and unknown kinds, counted,
 * to decide what to derive next. It only reads; demand never starts a derivation.
 */
public class KindDemandService {

  private final KindDemandStore store;

  /**
   * Wires the service.
   *
   * @param store {@code kind_demand}
   */
  public KindDemandService(KindDemandStore store) {
    this.store = Objects.requireNonNull(store, "KindDemandStore");
  }

  /**
   * The demand.
   *
   * @return the most asked first
   */
  public List<KindDemand> list() {
    return store.list();
  }
}
