package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.RuleProposalStore;
import com.krizaka.sovkora.tools.domain.model.law.ProposalStatus;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.Instant;
import java.util.*;

/** {@code rule_proposal} in memory, with the store's revision and supersede semantics. */
final class InMemoryRuleProposals implements RuleProposalStore {

  final List<RuleProposal> rows = new ArrayList<>();
  int writes;

  @Override
  public synchronized List<RuleProposal> record(
      String jurisdiction,
      String kindId,
      UUID derivationRunId,
      List<VettedProposal> proposals,
      Instant at) {
    writes++;
    List<RuleProposal> written = new ArrayList<>();
    for (VettedProposal p : proposals) {
      String ruleId = p.rule().ruleId();
      int revision =
          rows.stream()
                  .filter(r -> r.kindId().equals(kindId) && r.ruleId().equals(ruleId))
                  .mapToInt(RuleProposal::proposalRevision)
                  .max()
                  .orElse(0)
              + 1;
      if (p.status() == ProposalStatus.PROPOSED) {
        rows.replaceAll(
            r ->
                r.kindId().equals(kindId)
                        && r.ruleId().equals(ruleId)
                        && r.status() == ProposalStatus.PROPOSED
                    ? new RuleProposal(
                        r.id(),
                        r.jurisdiction(),
                        r.kindId(),
                        r.ruleId(),
                        r.proposalRevision(),
                        r.rule(),
                        r.derivationRunId(),
                        ProposalStatus.SUPERSEDED,
                        r.vetReport(),
                        null,
                        null,
                        null,
                        null,
                        r.createdAt())
                    : r);
      }
      RuleProposal row =
          new RuleProposal(
              UUID.randomUUID(),
              jurisdiction,
              kindId,
              ruleId,
              revision,
              p.rule(),
              derivationRunId,
              p.status(),
              p.report(),
              null,
              null,
              null,
              null,
              at);
      rows.add(row);
      written.add(row);
    }
    return written;
  }

  @Override
  public Optional<RuleProposal> find(UUID id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  @Override
  public List<RuleProposal> list(ProposalStatus status, String kindId) {
    return rows.stream()
        .filter(r -> status == null || r.status() == status)
        .filter(r -> kindId == null || r.kindId().equals(kindId))
        .sorted(Comparator.comparing(RuleProposal::createdAt).reversed())
        .toList();
  }

  /** The pull requests the promotions opened, in order. */
  final List<String> promotions = new ArrayList<>();

  @Override
  public synchronized Optional<RuleProposal> decide(
      UUID id,
      Set<ProposalStatus> from,
      ProposalStatus to,
      String reviewedBy,
      String note,
      Instant at) {
    Optional<RuleProposal> row = find(id);
    if (row.isEmpty() || !from.contains(row.get().status()) || row.get().packsPrUrl() != null) {
      return Optional.empty();
    }
    RuleProposal r = row.get();
    RuleProposal decided =
        new RuleProposal(
            r.id(),
            r.jurisdiction(),
            r.kindId(),
            r.ruleId(),
            r.proposalRevision(),
            r.rule(),
            r.derivationRunId(),
            to,
            r.vetReport(),
            reviewedBy,
            at,
            note,
            null,
            r.createdAt());
    replace(decided);
    return Optional.of(decided);
  }

  @Override
  public synchronized Optional<RuleProposal> revise(
      UUID previousId, Set<ProposalStatus> editable, VettedProposal edited, Instant at) {
    Optional<RuleProposal> row = find(previousId);
    if (row.isEmpty() || !editable.contains(row.get().status())) {
      return Optional.empty();
    }
    RuleProposal previous = row.get();
    if (edited.status() == ProposalStatus.PROPOSED) {
      rows.replaceAll(
          r ->
              r.kindId().equals(previous.kindId())
                      && r.ruleId().equals(previous.ruleId())
                      && (r.status() == ProposalStatus.PROPOSED
                          || r.status() == ProposalStatus.APPROVED)
                  ? withStatus(r, ProposalStatus.SUPERSEDED)
                  : r);
    }
    int revision =
        rows.stream()
                .filter(
                    r ->
                        r.kindId().equals(previous.kindId())
                            && r.ruleId().equals(previous.ruleId()))
                .mapToInt(RuleProposal::proposalRevision)
                .max()
                .orElse(0)
            + 1;
    RuleProposal next =
        new RuleProposal(
            UUID.randomUUID(),
            previous.jurisdiction(),
            previous.kindId(),
            previous.ruleId(),
            revision,
            edited.rule(),
            previous.derivationRunId(),
            edited.status(),
            edited.report(),
            null,
            null,
            null,
            null,
            at);
    rows.add(next);
    return Optional.of(next);
  }

  @Override
  public synchronized String promote(List<UUID> ids, PromotionStep step) {
    List<RuleProposal> locked = ids.stream().map(this::find).flatMap(Optional::stream).toList();
    String url = step.open(locked);
    for (RuleProposal r : locked) {
      replace(
          new RuleProposal(
              r.id(),
              r.jurisdiction(),
              r.kindId(),
              r.ruleId(),
              r.proposalRevision(),
              r.rule(),
              r.derivationRunId(),
              r.status(),
              r.vetReport(),
              r.reviewedBy(),
              r.reviewedAt(),
              r.decisionNote(),
              url,
              r.createdAt()));
    }
    promotions.add(url);
    return url;
  }

  /** Sets a row as a test needs it, beside the store's own writes. */
  synchronized void put(RuleProposal row) {
    if (find(row.id()).isPresent()) {
      replace(row);
    } else {
      rows.add(row);
    }
  }

  private void replace(RuleProposal row) {
    rows.replaceAll(r -> r.id().equals(row.id()) ? row : r);
  }

  private static RuleProposal withStatus(RuleProposal r, ProposalStatus status) {
    return new RuleProposal(
        r.id(),
        r.jurisdiction(),
        r.kindId(),
        r.ruleId(),
        r.proposalRevision(),
        r.rule(),
        r.derivationRunId(),
        status,
        r.vetReport(),
        r.reviewedBy(),
        r.reviewedAt(),
        r.decisionNote(),
        r.packsPrUrl(),
        r.createdAt());
  }
}
