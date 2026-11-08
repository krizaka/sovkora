package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/** The tenant's recommendations are switched off ({@code reco_settings.enabled = false}). */
public class RecommendationsDisabledException extends RuntimeException {

  public RecommendationsDisabledException() {
    super("recommendations are disabled for this tenant");
  }
}
