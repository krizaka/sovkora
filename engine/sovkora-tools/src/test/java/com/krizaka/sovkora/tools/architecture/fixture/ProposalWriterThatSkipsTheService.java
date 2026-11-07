package com.krizaka.sovkora.tools.architecture.fixture;

import com.krizaka.sovkora.tools.application.port.law.RuleProposalStore;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Planted for [LAW-004]: a class that writes {@code rule_proposal} around its one door. */
public final class ProposalWriterThatSkipsTheService {

  private ProposalWriterThatSkipsTheService() {}

  /** Writes directly to the store. */
  public static void write(RuleProposalStore store, List<VettedProposal> proposals) {
    store.record("QC", "kind", UUID.randomUUID(), proposals, Instant.EPOCH);
  }
}
