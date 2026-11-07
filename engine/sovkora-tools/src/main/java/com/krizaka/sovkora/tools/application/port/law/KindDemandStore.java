package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.KindDemand;
import java.util.List;

/** {@code kind_demand} (job-service): the unsupported and unknown kinds, counted. */
public interface KindDemandStore {

  /**
   * Every row.
   *
   * @return the rows, the most asked first
   */
  List<KindDemand> list();
}
