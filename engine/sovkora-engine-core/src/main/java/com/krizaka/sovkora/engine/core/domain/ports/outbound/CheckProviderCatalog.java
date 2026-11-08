package com.krizaka.sovkora.engine.core.domain.ports.outbound;

import com.krizaka.sovkora.engine.core.domain.model.checks.CheckProviderRow;
import java.util.List;

/** The provider rows the admin configured (endpoint, enabled, price, routing), read per call. */
public interface CheckProviderCatalog {

  /**
   * All the rows, enabled or not.
   *
   * @return the rows
   */
  List<CheckProviderRow> rows();
}
