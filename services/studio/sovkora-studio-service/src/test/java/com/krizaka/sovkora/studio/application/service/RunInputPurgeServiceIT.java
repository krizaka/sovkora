package com.krizaka.sovkora.studio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.studio.infrastructure.adapter.persistence.SharedStudioSchema;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * A run's input files are purged at its terminal state when its retention policy row says so
 * (krizaka-restricted#279), against the real Flyway schema and its seed row, in the shared
 * containers of {@link AbstractContainerIntegrationTest}.
 *
 * <p>The proof that the file is gone is not a list of columns that happen to be checked: the file's
 * bytes are searched for in <b>every table of the schema</b>, so a copy nobody thought of fails the
 * test.
 */
class RunInputPurgeServiceIT extends AbstractContainerIntegrationTest {

  private static final String TENANT = "550e8400-e29b-41d4-a716-446655440201";
  private static final String OTHER_TENANT = "550e8400-e29b-41d4-a716-446655440202";

  /** The Studio the migration seeds a purge row for. */
  private static final String DOCUMENT_STUDIO = "document-authenticity";

  private static final String FILE = "JVBERi0xLjcKJcfsj6IKUEFZU1RVQi1MRVMtQ0VSSVNJRVJT";

  private static HikariDataSource dataSource;
  private static JdbcTemplate jdbc;

  private RunInputPurgeService service;

  @BeforeAll
  static void openTheStudioDatabase() throws Exception {
    dataSource = SharedStudioSchema.dataSource(postgres());
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void closeIt() {
    dataSource.close();
  }

  @BeforeEach
  void freshData() {
    // The schema is shared with the other classes: only this class's tenants' rows are reset.
    jdbc.update(
        "DELETE FROM studio_outbox WHERE aggregate_id IN"
            + " (SELECT id::text FROM studio_run WHERE actor_id IN (?, ?))",
        TENANT,
        OTHER_TENANT);
    jdbc.update("DELETE FROM studio_run WHERE actor_id IN (?, ?)", TENANT, OTHER_TENANT);
    // The migration's own row stays: the tests run against the policy as it ships.
    jdbc.update(
        "DELETE FROM studio_run_retention_policy WHERE NOT (tenant_id = '*' AND studio_key = ?)",
        DOCUMENT_STUDIO);
    // The trail is append-only on purpose: it is not reset, the tests count their own runs' rows.
    service = new RunInputPurgeService(jdbc, new ObjectMapper());
  }

  /** A document check holding the file in its inputs, a step, an event and its dispatch. */
  private static UUID documentRun(String tenant, String studio, boolean finished) {
    UUID run =
        jdbc.queryForObject(
            "INSERT INTO studio_run (actor_id, studio_key, blueprint_version, status,"
                + " correlation_id, inputs, outputs, data_class, input_fingerprint, finished_at)"
                + " VALUES (?, ?, '1.4.0', ?, ?, ?::jsonb, ?::jsonb, 'SENSITIVE', 'fp-1', ?)"
                + " RETURNING id",
            UUID.class,
            tenant,
            studio,
            finished ? "SUCCEEDED" : "RUNNING",
            UUID.randomUUID().toString(),
            "{\"documentBase64\":\"" + FILE + "\",\"documentType\":\"pay-stub\"}",
            finished
                ? "{\"report\":\"Deux points à éclaircir\",\"signals\":{\"signals\":"
                    + "[{\"signalId\":\"incremental-save\",\"level\":\"inhabituel\"}]}}"
                : null,
            finished ? Timestamp.from(Instant.now()) : null);
    jdbc.update(
        "INSERT INTO studio_run_step (run_id, step_id, status, job_id, output)"
            + " VALUES (?, 'signals', ?, ?, ?::jsonb)",
        run,
        finished ? "SUCCEEDED" : "RUNNING",
        run.toString(),
        "{\"reportText\":\"Deux points\",\"documentBase64\":\"" + FILE + "\"}");
    jdbc.update(
        "INSERT INTO studio_run_event (run_id, seq, kind, detail)"
            + " VALUES (?, 1000, 'run.updated', ?::jsonb)",
        run,
        "{\"status\":\"RUNNING\",\"documentBase64\":\"" + FILE + "\"}");
    jdbc.update(
        "INSERT INTO studio_outbox (aggregate_id, event_type, exchange, message_id, payload)"
            + " VALUES (?, 'job.validation.signals', 'orazaka.jobs', ?, ?::jsonb)",
        run.toString(),
        run.toString(),
        "{\"jobId\":\""
            + run
            + "\",\"featureKey\":\"orazaka.validation.document.signals\",\"dataClass\":"
            + "\"SENSITIVE\",\"payload\":{\"documentBase64\":\""
            + FILE
            + "\"}}");
    jdbc.update(
        "INSERT INTO studio_outbox (aggregate_id, event_type, payload)"
            + " VALUES (?, 'evt.studio.run.succeeded', ?::jsonb)",
        run.toString(),
        "{\"runId\":\"" + run + "\"}");
    return run;
  }

  private static void finish(UUID run) {
    jdbc.update(
        "UPDATE studio_run SET status = 'SUCCEEDED', finished_at = now() WHERE id = ?", run);
  }

  /** Every table of the schema whose rows still hold the file's bytes. */
  private static List<String> tablesHoldingTheFile() {
    List<String> holding = new ArrayList<>();
    for (String table :
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'",
            String.class)) {
      Integer rows =
          jdbc.queryForObject(
              "SELECT count(*) FROM " + table + " t WHERE t::text LIKE ?",
              Integer.class,
              "%" + FILE + "%");
      if (rows != null && rows > 0) {
        holding.add(table);
      }
    }
    return holding;
  }

  private static int auditRows(UUID run) {
    Integer rows =
        jdbc.queryForObject(
            "SELECT count(*) FROM studio_run_purge_audit WHERE run_id = ?", Integer.class, run);
    return rows == null ? 0 : rows;
  }

  @Test
  @DisplayName(
      "[restricted#279] a finished document check loses its file everywhere; findings, id and"
          + " fingerprint stay")
  void aFinishedDocumentCheckLosesItsFile() {
    UUID run = documentRun(TENANT, DOCUMENT_STUDIO, true);
    assertThat(tablesHoldingTheFile()).isNotEmpty();

    assertThat(service.purge(run, TENANT)).containsExactly("documentBase64");

    assertThat(tablesHoldingTheFile()).as("no copy of the file is left in any table").isEmpty();
    var row =
        jdbc.queryForMap(
            "SELECT id, status, input_fingerprint, inputs::text AS inputs,"
                + " outputs::text AS outputs, inputs_purged_at FROM studio_run WHERE id = ?",
            run);
    assertThat(row.get("id")).isEqualTo(run);
    assertThat(row.get("status")).isEqualTo("SUCCEEDED");
    assertThat(row.get("input_fingerprint")).isEqualTo("fp-1");
    assertThat((String) row.get("inputs")).doesNotContain("documentBase64").contains("pay-stub");
    assertThat((String) row.get("outputs")).contains("incremental-save").contains("éclaircir");
    assertThat(row.get("inputs_purged_at")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT output ->> 'reportText' FROM studio_run_step WHERE run_id = ?",
                String.class,
                run))
        .isEqualTo("Deux points");
    assertThat(
            jdbc.queryForObject(
                "SELECT payload ->> 'jobId' FROM studio_outbox WHERE message_id = ?",
                String.class,
                run.toString()))
        .as("the dispatch keeps its envelope")
        .isEqualTo(run.toString());
  }

  @Test
  @DisplayName(
      "[restricted#279] one run.inputs_purged row per purge, with the field names and never the"
          + " content; the trail is append-only")
  void thePurgeIsAuditedOnce() {
    UUID run = documentRun(TENANT, DOCUMENT_STUDIO, true);

    service.purge(run, TENANT);
    assertThat(service.purge(run, TENANT)).as("a second purge has nothing to do").isEmpty();
    assertThat(service.purgeFinished(10)).as("nor has the sweeper").isZero();

    assertThat(auditRows(run)).isEqualTo(1);
    var audit =
        jdbc.queryForMap(
            "SELECT tenant_id, studio_key, event, fields::text AS fields"
                + " FROM studio_run_purge_audit WHERE run_id = ?",
            run);
    assertThat(audit.get("tenant_id")).isEqualTo(TENANT);
    assertThat(audit.get("studio_key")).isEqualTo(DOCUMENT_STUDIO);
    assertThat(audit.get("event")).isEqualTo("run.inputs_purged");
    assertThat(audit.get("fields")).isEqualTo("[\"documentBase64\"]");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE studio_run_purge_audit SET fields = '[]' WHERE run_id = ?", run))
        .hasMessageContaining("append-only");
  }

  @Test
  @DisplayName("[restricted#279] a run of a Studio with no purge row, or a KEEP row, is untouched")
  void aKeptRunIsUntouched() {
    UUID unruled = documentRun(TENANT, "trade-showcase", true);
    jdbc.update(
        "INSERT INTO studio_run_retention_policy (tenant_id, studio_key, inputs_mode, input_fields)"
            + " VALUES ('*', 'kept-studio', 'KEEP', '[\"documentBase64\"]')");
    UUID kept = documentRun(TENANT, "kept-studio", true);

    assertThat(service.purge(unruled, TENANT)).isEmpty();
    assertThat(service.purge(kept, TENANT)).isEmpty();
    assertThat(service.purgeFinished(10)).isZero();

    for (UUID run : List.of(unruled, kept)) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT inputs ->> 'documentBase64' FROM studio_run WHERE id = ?",
                  String.class,
                  run))
          .isEqualTo(FILE);
      assertThat(auditRows(run)).isZero();
    }
  }

  @Test
  @DisplayName(
      "[restricted#279] a run still going keeps its file; once it ends the sweeper purges it")
  void theSweeperIsTheSafetyNet() {
    UUID run = documentRun(TENANT, DOCUMENT_STUDIO, false);

    assertThat(service.purge(run, TENANT)).isEmpty();
    assertThat(service.purgeFinished(10)).isZero();
    assertThat(tablesHoldingTheFile()).isNotEmpty();

    finish(run);
    assertThat(service.purgeFinished(10)).isEqualTo(1);
    assertThat(tablesHoldingTheFile()).isEmpty();
    assertThat(auditRows(run)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[restricted#279] a tenant's row applies to that tenant only, and never lifts a purge that"
          + " applies to every tenant")
  void rowsAreScopedToTheirTenant() {
    jdbc.update(
        "INSERT INTO studio_run_retention_policy (tenant_id, studio_key, inputs_mode, input_fields)"
            + " VALUES (?, 'lease-check', 'PURGE_AT_TERMINAL', '[\"documentBase64\"]'),"
            + " (?, ?, 'KEEP', '[]')",
        TENANT,
        TENANT,
        DOCUMENT_STUDIO);
    UUID mine = documentRun(TENANT, "lease-check", true);
    UUID theirs = documentRun(OTHER_TENANT, "lease-check", true);
    UUID document = documentRun(TENANT, DOCUMENT_STUDIO, true);

    assertThat(service.purge(theirs, OTHER_TENANT)).isEmpty();
    assertThat(service.purge(mine, OTHER_TENANT)).as("a run is purged as its own tenant").isEmpty();
    assertThat(service.purge(mine, TENANT)).containsExactly("documentBase64");
    assertThat(service.purge(document, TENANT))
        .as("the tenant's KEEP does not lift the purge every tenant has")
        .containsExactly("documentBase64");
    assertThat(
            jdbc.queryForObject(
                "SELECT inputs ->> 'documentBase64' FROM studio_run WHERE id = ?",
                String.class,
                theirs))
        .isEqualTo(FILE);
  }

  @Test
  @DisplayName("[restricted#279] a purge row must name the fields it purges")
  void aPurgeRowNamesItsFields() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO studio_run_retention_policy (studio_key, inputs_mode)"
                        + " VALUES ('nameless', 'PURGE_AT_TERMINAL')"))
        .hasMessageContaining("ck_run_retention_purge_names_fields");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO studio_run_retention_policy (studio_key, inputs_mode)"
                        + " VALUES ('typo', 'PURGE')"))
        .hasMessageContaining("ck_run_retention_mode");
  }
}
