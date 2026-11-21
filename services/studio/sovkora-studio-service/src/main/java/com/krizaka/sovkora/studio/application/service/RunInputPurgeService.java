package com.krizaka.sovkora.studio.application.service;

import com.krizaka.sovkora.studio.domain.model.InputRetention;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Removes a finished run's input files when its retention policy row says so
 * (krizaka-restricted#279, Loi 25 minimisation).
 *
 * <p>The retention window deletes a run long after it ended (30 days for a SENSITIVE one); a
 * document check needs its file only while it runs. A {@code studio_run_retention_policy} row whose
 * mode is {@link InputRetention#PURGE_AT_TERMINAL} names the input fields that carry a file; when
 * the run reaches a terminal state those fields are removed from every copy this context holds: the
 * run's inputs and outputs, its steps' outputs, its events, and the body of the dispatch commands
 * its steps were sent in (a finished run's command is never sent again). The run id, its status,
 * counters, input fingerprint and findings stay. The engine names no Studio and no field: both are
 * the row's.
 *
 * <p>Rows apply per tenant (the run's actor) or to every tenant ({@code '*'}), and the strictest
 * wins: a tenant's {@code KEEP} never lifts a purge that applies to everyone, as a retention
 * setting may only shorten what is kept (ADR-051).
 *
 * <p>Idempotent: the run is stamped {@code inputs_purged_at} in the same statement that removes the
 * fields, so a second call, or the sweeper's, finds nothing to do. Each purge appends one {@code
 * run.inputs_purged} row to {@code studio_run_purge_audit}, with the field names and never their
 * content. It runs in the transaction of the terminal transition that calls it, and {@link
 * #purgeFinished} is the sweeper's safety net for a path that did not.
 */
@Service
public class RunInputPurgeService {

  private static final Logger logger = LoggerFactory.getLogger(RunInputPurgeService.class);

  static final String PURGED_EVENT = "run.inputs_purged";

  /** The field names of the policy rows that apply to one run, as a JSON array. */
  private static final String FIELDS_OF_RUN =
      """
      SELECT COALESCE(jsonb_agg(DISTINCT f.name ORDER BY f.name), '[]'::jsonb)::text
        FROM studio_run r
        JOIN studio_run_retention_policy p
          ON p.studio_key = r.studio_key AND p.tenant_id IN ('*', r.actor_id)
         AND p.inputs_mode = ?
       CROSS JOIN LATERAL jsonb_array_elements_text(p.input_fields) AS f(name)
       WHERE r.id = ? AND r.actor_id = ?
         AND r.finished_at IS NOT NULL AND r.inputs_purged_at IS NULL
      """;

  private static final String NAMES = "ARRAY(SELECT jsonb_array_elements_text(?::jsonb))";

  private static final String PURGE_RUN =
      "UPDATE studio_run SET inputs = inputs - "
          + NAMES
          + ", outputs = CASE WHEN jsonb_typeof(outputs) = 'object' THEN outputs - "
          + NAMES
          + " ELSE outputs END, inputs_purged_at = now()"
          + " WHERE id = ? AND actor_id = ? AND finished_at IS NOT NULL"
          + " AND inputs_purged_at IS NULL";

  private static final String PURGE_STEPS =
      "UPDATE studio_run_step SET output = output - "
          + NAMES
          + " WHERE run_id = ? AND jsonb_typeof(output) = 'object'"
          + " AND run_id IN (SELECT id FROM studio_run WHERE id = ? AND actor_id = ?)";

  private static final String PURGE_EVENTS =
      "UPDATE studio_run_event SET detail = detail - "
          + NAMES
          + " WHERE run_id = ? AND jsonb_typeof(detail) = 'object'"
          + " AND run_id IN (SELECT id FROM studio_run WHERE id = ? AND actor_id = ?)";

  /**
   * A step dispatch carries its inputs, resolved from the run's, in {@code payload.payload}; a stop
   * command ({@code job.cancel.*}) carries a job id only. The envelope (job id, feature, class)
   * stays, so the outbox still says what was sent.
   */
  private static final String PURGE_COMMANDS =
      "UPDATE studio_outbox SET payload = jsonb_set(payload, '{payload}', '{}'::jsonb)"
          + " WHERE aggregate_id = ? AND message_id IS NOT NULL"
          + " AND event_type NOT LIKE 'job.cancel.%' AND payload -> 'payload' IS NOT NULL"
          + " AND aggregate_id IN (SELECT id::text FROM studio_run WHERE id = ? AND actor_id = ?)";

  private static final String AUDIT =
      "INSERT INTO studio_run_purge_audit (run_id, tenant_id, studio_key, event, fields)"
          + " SELECT id, actor_id, studio_key, ?, ?::jsonb FROM studio_run"
          + " WHERE id = ? AND actor_id = ?";

  /** Finished runs not yet purged that a purge row applies to, oldest first. */
  private static final String PENDING =
      """
      SELECT r.id, r.actor_id FROM studio_run r
       WHERE r.finished_at IS NOT NULL AND r.inputs_purged_at IS NULL
         AND EXISTS (SELECT 1 FROM studio_run_retention_policy p
                      WHERE p.studio_key = r.studio_key AND p.tenant_id IN ('*', r.actor_id)
                        AND p.inputs_mode = ?)
       ORDER BY r.finished_at LIMIT ? FOR UPDATE OF r SKIP LOCKED
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public RunInputPurgeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
  }

  /**
   * Purges a finished run's input files if a policy row says so; does nothing otherwise, or when
   * the run is still going or already purged.
   *
   * @param runId the run
   * @param actorId the run's actor, its tenant
   * @return the names of the fields purged, empty when nothing was
   */
  @Transactional
  public List<String> purge(UUID runId, String actorId) {
    String fields =
        jdbcTemplate.queryForObject(
            FIELDS_OF_RUN, String.class, InputRetention.PURGE_AT_TERMINAL.name(), runId, actorId);
    List<String> names = names(fields);
    if (names.isEmpty()) {
      return List.of();
    }
    int updated = jdbcTemplate.update(PURGE_RUN, fields, fields, runId, actorId);
    if (updated == 0) {
      // Another transaction purged it between the read and the write: nothing left to do here.
      return List.of();
    }
    jdbcTemplate.update(PURGE_STEPS, fields, runId, runId, actorId);
    jdbcTemplate.update(PURGE_EVENTS, fields, runId, runId, actorId);
    jdbcTemplate.update(PURGE_COMMANDS, runId.toString(), runId, actorId);
    jdbcTemplate.update(AUDIT, PURGED_EVENT, fields, runId, actorId);
    logger.info("Purged the input fields {} of run {} at its terminal state", names, runId);
    return names;
  }

  /**
   * The sweeper's safety net: purges the finished runs a terminal transition left unpurged.
   *
   * @param limit the most runs to purge in one call
   * @return how many runs were purged
   */
  @Transactional
  public int purgeFinished(int limit) {
    List<String[]> pending =
        jdbcTemplate.query(
            PENDING,
            (rs, rowNum) -> new String[] {rs.getString(1), rs.getString(2)},
            InputRetention.PURGE_AT_TERMINAL.name(),
            Math.max(1, limit));
    int purged = 0;
    for (String[] run : pending) {
      if (!purge(UUID.fromString(run[0]), run[1]).isEmpty()) {
        purged++;
      }
    }
    return purged;
  }

  private List<String> names(String json) {
    return List.of(objectMapper.readValue(json, String[].class));
  }
}
