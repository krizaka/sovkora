package com.krizaka.sovkora.tools.domain.model.law;

/** Planted for [LAW-003]: a class that makes a vetted proposal without the vet. */
final class PlantedVetBypass {

  private PlantedVetBypass() {}

  static VettedProposal unvetted(ProposedRule rule) {
    return new VettedProposal(rule, new VetReport("1", "2026-10-20", java.util.List.of()));
  }
}
