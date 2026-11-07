package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.LawChange;

/** Where a detected change goes for review: a pull request in the rulesets repository. */
public interface LawChangeRequests {

  /**
   * Opens the review request for one change.
   *
   * @param change the change, with its previous and current text
   * @return the URL of the request
   */
  String open(LawChange change);
}
