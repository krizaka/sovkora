package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.sovkora.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.sovkora.persistence.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.domain.ports.inbound.OutboxStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Persists a control verdict through the transactional outbox (ADR-092 §4 rule 7): the row joins
 * the transaction of the run's state, so a rolled-back run leaves no verdict and a committed one
 * always has it. The payload is the record Loi 25 art. 12.1 asks for: run, drift reason, human
 * actor, time, action taken.
 */
@Service
class OutboxControlVerdictRecorderAdapter implements ControlVerdictRecorder {

  static final String AGGREGATE_TYPE = "control-verdict";
  static final String EXCHANGE = "orazaka.events";
  static final String ROUTING_KEY = "evt.control.verdict";

  private final OutboxStore outboxStore;

  OutboxControlVerdictRecorderAdapter(OutboxStore outboxStore) {
    this.outboxStore = Objects.requireNonNull(outboxStore, "OutboxStore cannot be null");
  }

  @Override
  public void record(ControlVerdictRecord verdict) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("runId", verdict.runId());
    payload.put("driftReason", verdict.driftReason());
    payload.put("humanActorId", verdict.humanActorId());
    payload.put("timestamp", verdict.timestamp().toString());
    payload.put("actionTaken", verdict.actionTaken());
    outboxStore.append(
        new OutboxMessage(AGGREGATE_TYPE, verdict.runId(), EXCHANGE, ROUTING_KEY, payload));
  }
}
