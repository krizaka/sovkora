package com.krizaka.sovkora.tools.domain.port;

import com.krizaka.sovkora.tools.domain.model.research.FetchRequest;
import com.krizaka.sovkora.tools.domain.model.research.FetchResult;

/** Reads one page for grounded research, through a declared source, with every guard (ADR-087). */
public interface PageFetcher {

  /**
   * Reads the page, or says why not. Every call — made, served from the cache or refused — leaves
   * one audit line.
   *
   * @param request what to read and for whom
   * @return the document or the refusal
   */
  FetchResult fetch(FetchRequest request);
}
