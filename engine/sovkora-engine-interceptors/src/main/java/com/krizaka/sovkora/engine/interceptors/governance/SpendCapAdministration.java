package com.krizaka.sovkora.engine.interceptors.governance;

import java.util.List;

/**
 * The administrator's side of the provider-spend caps. There is no removal: a cap is created or
 * changed, never deleted, so a scope never runs uncapped.
 */
public interface SpendCapAdministration {

  /** Every cap row. */
  List<SpendCap> caps();

  /** Every price row. */
  List<ProviderPrice> prices();

  /**
   * Creates a cap or changes its amount or action, and records the old and new values.
   *
   * @param cap the new state of the scope's cap
   * @return the stored cap
   * @throws IllegalArgumentException when a downgrade names a model with no chat price row
   */
  SpendCap put(SpendCap cap);
}
