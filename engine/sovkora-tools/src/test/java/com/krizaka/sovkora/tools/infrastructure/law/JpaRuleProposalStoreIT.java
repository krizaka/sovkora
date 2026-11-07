package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.DerivationFixtures.Planted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code rule_proposal} on PostgreSQL: the JSONB round trip, the revisions and the supersede, the
 * statuses the table accepts, and two derivations of the same kind at once taking distinct
 * revisions under the kind's lock. Every test commits for real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaRuleProposalStore.class)
class JpaRuleProposalStoreIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Instant T0 = Instant.parse("2026-10-24T06:00:00Z");

  @Autowired private JpaRuleProposalStore store;
  @Autowired private JdbcTemplate jdbc;

  private String kind;

  @BeforeEach
  void fresh() {
    LawTestSchema.apply(jdbc);
    // One kind per test: the tests share the database and commit.
    kind = "kind-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static List<VettedProposal> visit1931() {
    return new RuleProposalVet(DerivationFixtures.visitCorpus(), DerivationFixtures.NOTICE_DAY, 30)
        .vet(DerivationFixtures.visitAnswers().get("1931"));
  }

  private static VettedProposal rejected() {
    Planted subject =
        DerivationFixtures.planted().stream()
            .filter(p -> p.reason() == VetReason.SUBJECT_MISSING)
            .findFirst()
            .orElseThrow();
    return new RuleProposalVet(DerivationFixtures.visitCorpus(), DerivationFixtures.NOTICE_DAY, 30)
        .vet(subject.rules())
        .getFirst();
  }

  @Test
  @DisplayName("a proposal round-trips through JSONB: the rule, its law and its vet report")
  void roundTrip() {
    UUID run = UUID.randomUUID();
    VettedProposal refused = rejected();
    List<VettedProposal> batch = new ArrayList<>(visit1931());
    batch.add(refused);

    List<RuleProposal> written = store.record("QC", kind, run, batch, T0);

    assertThat(written).hasSize(3);
    for (RuleProposal row : written) {
      RuleProposal read = store.find(row.id()).orElseThrow();
      assertThat(read.rule()).isEqualTo(row.rule());
      assertThat(read.vetReport()).isEqualTo(row.vetReport());
      assertThat(read.status()).isEqualTo(row.status());
      assertThat(read.derivationRunId()).isEqualTo(run);
      assertThat(read.proposalRevision()).isEqualTo(1);
    }
    assertThat(store.find(written.get(2).id()).orElseThrow().vetReport().reasons())
        .containsExactly(VetReason.SUBJECT_MISSING);
    assertThat(
            jdbc.queryForObject(
                "select law->0->>'ref' from rule_proposal where id = ?",
                String.class,
                written.getFirst().id()))
        .isEqualTo("1931");
    assertThat(store.list(ProposalStatus.PROPOSED, kind)).hasSize(2);
    assertThat(store.list(ProposalStatus.REJECTED_AUTO, kind)).hasSize(1);
    assertThat(store.list(null, kind)).hasSize(3);
  }

  @Test
  @DisplayName("a new proposal of a rule is the next revision and supersedes the unreviewed one")
  void revisionsAndSupersede() {
    store.record("QC", kind, UUID.randomUUID(), visit1931(), T0);
    List<RuleProposal> second =
        store.record("QC", kind, UUID.randomUUID(), visit1931(), T0.plusSeconds(60));

    assertThat(second).allMatch(p -> p.proposalRevision() == 2);
    assertThat(store.list(ProposalStatus.SUPERSEDED, kind))
        .hasSize(2)
        .allMatch(p -> p.proposalRevision() == 1);
    assertThat(store.list(ProposalStatus.PROPOSED, kind)).hasSize(2);
  }

  @Test
  @DisplayName("a rejected revision never supersedes a proposal that passed")
  void aRejectedRevisionSupersedesNothing() {
    store.record("QC", kind, UUID.randomUUID(), List.of(rejected()), T0);
    VettedProposal sameRuleRejected = rejected();
    store.record("QC", kind, UUID.randomUUID(), List.of(sameRuleRejected), T0);

    assertThat(store.list(ProposalStatus.REJECTED_AUTO, kind))
        .extracting(RuleProposal::proposalRevision)
        .containsExactlyInAnyOrder(1, 2);
    assertThat(store.list(ProposalStatus.SUPERSEDED, kind)).isEmpty();
  }

  @Test
  @DisplayName("two derivations of the same kind at once take distinct revisions, one PROPOSED")
  void concurrentDerivationsTakeDistinctRevisions() throws Exception {
    int writers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(writers);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> writes = new ArrayList<>();
      for (int i = 0; i < writers; i++) {
        writes.add(
            pool.submit(
                () -> {
                  go.await();
                  store.record("QC", kind, UUID.randomUUID(), visit1931(), T0);
                  return null;
                }));
      }
      go.countDown();
      for (Future<?> write : writes) {
        write.get();
      }
    } finally {
      pool.shutdownNow();
    }

    List<Integer> revisions =
        jdbc.queryForList(
            "select proposal_revision from rule_proposal where kind_id = ? and rule_id ="
                + " 'QC-VISIT-001' order by 1",
            Integer.class,
            kind);
    assertThat(revisions).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from rule_proposal where kind_id = ? and rule_id = 'QC-VISIT-001'"
                    + " and status = 'PROPOSED'",
                Integer.class,
                kind))
        .isEqualTo(1);
  }

  private static final java.util.Set<ProposalStatus> WAITING =
      java.util.EnumSet.of(ProposalStatus.PROPOSED);
  private static final java.util.Set<ProposalStatus> EDITABLE =
      java.util.EnumSet.of(
          ProposalStatus.PROPOSED,
          ProposalStatus.APPROVED,
          ProposalStatus.REJECTED,
          ProposalStatus.REJECTED_AUTO);

  @Test
  @DisplayName("a decision is written only on a row still in the expected status")
  void aDecisionIsConditional() {
    RuleProposal row = store.record("QC", kind, UUID.randomUUID(), visit1931(), T0).getFirst();

    RuleProposal approved =
        store
            .decide(row.id(), WAITING, ProposalStatus.APPROVED, "eric-admin", "ok", T0)
            .orElseThrow();

    assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(approved.reviewedBy()).isEqualTo("eric-admin");
    assertThat(approved.reviewedAt()).isEqualTo(T0);
    assertThat(approved.decisionNote()).isEqualTo("ok");
    assertThat(store.decide(row.id(), WAITING, ProposalStatus.APPROVED, "eric-admin", null, T0))
        .isEmpty();
    assertThat(store.decide(UUID.randomUUID(), WAITING, ProposalStatus.APPROVED, "x", null, T0))
        .isEmpty();
  }

  @Test
  @DisplayName("an edit after approval is the next revision and the approval is superseded")
  void anEditAfterApprovalSupersedesIt() {
    RuleProposal row = store.record("QC", kind, UUID.randomUUID(), visit1931(), T0).getFirst();
    store.decide(row.id(), WAITING, ProposalStatus.APPROVED, "eric-admin", null, T0);

    RuleProposal edited =
        store.revise(row.id(), EDITABLE, visit1931().getFirst(), T0.plusSeconds(60)).orElseThrow();

    assertThat(edited.proposalRevision()).isEqualTo(2);
    assertThat(edited.status()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(edited.derivationRunId()).isEqualTo(row.derivationRunId());
    assertThat(store.find(row.id()).orElseThrow().status()).isEqualTo(ProposalStatus.SUPERSEDED);
    assertThat(store.revise(row.id(), EDITABLE, visit1931().getFirst(), T0)).isEmpty();
  }

  @Test
  @DisplayName("a failed promotion step writes nothing on the rows")
  void aFailedPromotionWritesNothing() {
    List<UUID> ids =
        store.record("QC", kind, UUID.randomUUID(), visit1931(), T0).stream()
            .map(RuleProposal::id)
            .toList();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                store.promote(
                    ids,
                    locked -> {
                      throw new IllegalStateException("GitHub is down");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ids)
        .allSatisfy(id -> assertThat(store.find(id).orElseThrow().packsPrUrl()).isNull());
  }

  @Test
  @DisplayName("two promotions of the same rows at once open one pull request")
  void concurrentPromotionsOpenOnePullRequest() throws Exception {
    List<UUID> ids = new ArrayList<>();
    for (RuleProposal row : store.record("QC", kind, UUID.randomUUID(), visit1931(), T0)) {
      store.decide(row.id(), WAITING, ProposalStatus.APPROVED, "eric-admin", null, T0);
      ids.add(row.id());
    }
    java.util.concurrent.atomic.AtomicInteger opened =
        new java.util.concurrent.atomic.AtomicInteger();
    int promoters = 4;
    ExecutorService pool = Executors.newFixedThreadPool(promoters);
    List<Future<String>> results = new ArrayList<>();
    try {
      CountDownLatch go = new CountDownLatch(1);
      for (int i = 0; i < promoters; i++) {
        results.add(
            pool.submit(
                () -> {
                  go.await();
                  return store.promote(
                      ids,
                      locked -> {
                        if (locked.stream().anyMatch(r -> r.packsPrUrl() != null)) {
                          throw new IllegalStateException("already in review");
                        }
                        return "https://github.com/krizaka/sovkora-packs/pull/"
                            + opened.incrementAndGet();
                      });
                }));
      }
      go.countDown();
      int refused = 0;
      for (Future<String> result : results) {
        try {
          result.get();
        } catch (java.util.concurrent.ExecutionException e) {
          assertThat(e.getCause()).hasMessage("already in review");
          refused++;
        }
      }
      assertThat(refused).isEqualTo(promoters - 1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(opened.get()).isEqualTo(1);
    assertThat(ids)
        .allSatisfy(
            id ->
                assertThat(store.find(id).orElseThrow().packsPrUrl())
                    .isEqualTo("https://github.com/krizaka/sovkora-packs/pull/1"));
  }
}
