package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import static org.assertj.core.api.Assertions.*;

import com.krizaka.sovkora.engine.interceptors.governance.*;
import com.krizaka.sovkora.test.support.AbstractContainerIntegrationTest;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The spend rows against a real PostgreSQL, with the shipped governance V2 migration: the monthly
 * counter is atomic under concurrent turns, a new month starts at zero, an alert fires once per
 * threshold, and a cap can be changed (with its trail) but its row survives.
 */
@SpringBootTest(classes = SpendLedgerStoreIT.Cfg.class)
@DisplayName("SpendLedgerStore — real PostgreSQL")
class SpendLedgerStoreIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackageClasses = SpendLedgerStore.class)
  @EnableJpaRepositories(basePackageClasses = SpendLedgerStore.class)
  static class Cfg {
    @Bean
    SpendLedgerStore store(
        SpendCapRepository caps, ProviderPriceRepository prices, ProviderSpendRepository spend) {
      return new SpendLedgerStore(caps, prices, spend);
    }
  }

  private static final SpendScope SCOPE = new SpendScope("dev", "verikora");

  @Autowired private SpendLedgerStore store;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void schema() throws Exception {
    jdbc.execute(
        "DROP TABLE IF EXISTS provider_spend_cap_change, provider_spend_alert,"
            + " provider_spend_threshold, provider_spend, provider_spend_cap, provider_price CASCADE");
    jdbc.execute(
        Files.readString(
            Path.of(
                "../sovkora-engine-persistence/src/main/resources/db/governance/V2__provider_spend_cap.sql")));
  }

  @Test
  @DisplayName("sixteen turns finishing together all count: the add is one atomic statement")
  void concurrentAddsAllCount() {
    YearMonth month = YearMonth.of(2026, 11);

    IntStream.range(0, 16)
        .mapToObj(
            i -> CompletableFuture.runAsync(() -> store.add(SCOPE, month, new BigDecimal("0.25"))))
        .toList()
        .forEach(CompletableFuture::join);

    assertThat(store.spentIn(SCOPE, month)).isEqualByComparingTo("4");
  }

  @Test
  @DisplayName("a month's spend is its own: the next month starts at zero")
  void monthsAreSeparate() {
    store.add(SCOPE, YearMonth.of(2026, 11), new BigDecimal("7"));

    assertThat(store.spentIn(SCOPE, YearMonth.of(2026, 12))).isZero();
    assertThat(store.spentIn(SCOPE, YearMonth.of(2026, 11))).isEqualByComparingTo("7");
  }

  @Test
  @DisplayName("an alert is marked fired once per scope, month and threshold")
  void alertOnce() {
    YearMonth month = YearMonth.of(2026, 11);

    assertThat(store.alertThresholds()).containsExactly(50, 80, 100);
    assertThat(store.markAlertFired(SCOPE, month, 50)).isTrue();
    assertThat(store.markAlertFired(SCOPE, month, 50)).isFalse();
    assertThat(store.markAlertFired(SCOPE, YearMonth.of(2026, 12), 50)).isTrue();
  }

  @Test
  @DisplayName("the seeded dev cap is read, the price of a model is found")
  void seededRows() {
    assertThat(store.capOf(SCOPE))
        .get()
        .extracting(SpendCap::action)
        .isEqualTo(SpendCapAction.REFUSE);
    assertThat(store.priceOf("do-inference")).isPresent();
  }

  @Test
  @DisplayName("a cap is changed with its trail, and a downgrade needs a priced chat model")
  void putChangesAndRecords() {
    store.put(new SpendCap(SCOPE, new BigDecimal("500"), SpendCapAction.DOWNGRADE, "ollama"));

    assertThat(store.capOf(SCOPE))
        .get()
        .extracting(SpendCap::action)
        .isEqualTo(SpendCapAction.DOWNGRADE);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM provider_spend_cap_change WHERE new_action = 'DOWNGRADE'",
                Long.class))
        .isEqualTo(1L);
    assertThatThrownBy(
            () -> store.put(new SpendCap(SCOPE, BigDecimal.TEN, SpendCapAction.DOWNGRADE, "nope")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
