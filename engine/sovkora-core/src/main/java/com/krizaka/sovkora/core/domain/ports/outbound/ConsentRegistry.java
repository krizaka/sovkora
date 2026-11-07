package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;

/** Whether a subject has consented, now, to one purpose of external checks (Loi 25). */
public interface ConsentRegistry {

  /**
   * Whether consent is granted and not withdrawn for this subject and purpose.
   *
   * @param subjectId the opaque platform id of the subject
   * @param purpose the purpose
   * @return true only when a current consent exists
   */
  boolean isGranted(String subjectId, CheckPurpose purpose);
}
