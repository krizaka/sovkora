package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.domain.model.law.DerivationFixtures;
import com.krizaka.sovkora.tools.domain.model.law.ProposalStatus;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposalVet;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The door to {@code rule_proposal}: vetted rules only, named by jurisdiction and kind. */
class RuleProposalServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

  private final InMemoryRuleProposals store = new InMemoryRuleProposals();
  private final RuleProposalService service =
      new RuleProposalService(store, Clock.fixed(NOW, ZoneOffset.UTC));

  private static List<VettedProposal> vetted() {
    return new RuleProposalVet(DerivationFixtures.visitCorpus(), DerivationFixtures.NOTICE_DAY, 30)
        .vet(DerivationFixtures.visitAnswers().get("1931"));
  }

  @Test
  void a_proposal_is_written_with_its_status_its_report_and_the_clock_instant() {
    UUID run = UUID.randomUUID();

    List<RuleProposal> written = service.record(" qc ", " qc-visit-notice ", run, vetted());

    assertThat(written).hasSize(2);
    assertThat(written)
        .allSatisfy(
            p -> {
              assertThat(p.jurisdiction()).isEqualTo("QC");
              assertThat(p.kindId()).isEqualTo("qc-visit-notice");
              assertThat(p.status()).isEqualTo(ProposalStatus.PROPOSED);
              assertThat(p.createdAt()).isEqualTo(NOW);
              assertThat(p.vetReport()).isNotNull();
            });
    assertThat(service.find(written.getFirst().id())).contains(written.getFirst());
    assertThat(service.list(ProposalStatus.PROPOSED, " ")).hasSize(2);
    assertThat(service.list(ProposalStatus.REJECTED_AUTO, null)).isEmpty();
  }

  @Test
  void nothing_to_record_writes_nothing() {
    assertThat(service.record("QC", "qc-visit-notice", UUID.randomUUID(), List.of())).isEmpty();
    assertThat(store.writes).isZero();
  }

  @Test
  void a_proposal_names_its_jurisdiction_and_its_kind() {
    UUID run = UUID.randomUUID();
    List<VettedProposal> vetted = vetted();

    assertThatThrownBy(() -> service.record(" ", "qc-visit-notice", run, vetted))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.record("QC", "", run, vetted))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.record("QC", "qc-visit-notice", null, vetted))
        .isInstanceOf(NullPointerException.class);
    assertThat(store.writes).isZero();
  }
}
