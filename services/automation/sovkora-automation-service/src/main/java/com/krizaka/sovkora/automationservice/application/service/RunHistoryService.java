package com.krizaka.sovkora.automationservice.application.service;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.RunNotFoundException;
import com.krizaka.sovkora.automationservice.domain.model.AutomationRun;
import com.krizaka.sovkora.automationservice.domain.model.RunDetail;
import com.krizaka.sovkora.automationservice.domain.port.AutomationRepository;
import com.krizaka.sovkora.automationservice.domain.port.RunHistoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The owner's run history: newest first, scoped by the owner like everything else. */
@Service
public class RunHistoryService {

  /** The most runs one page returns. */
  public static final int MAX_PAGE = 200;

  private static final int DEFAULT_PAGE = 50;

  private final RunHistoryRepository runs;
  private final AutomationRepository automations;

  public RunHistoryService(RunHistoryRepository runs, AutomationRepository automations) {
    this.runs = Objects.requireNonNull(runs, "RunHistoryRepository cannot be null");
    this.automations = Objects.requireNonNull(automations, "AutomationRepository cannot be null");
  }

  /**
   * The owner's runs.
   *
   * @param ownerId the owner
   * @param automationId one automation, or null for all
   * @param limit the most to return, a default when null, never above {@link #MAX_PAGE}
   * @return the runs, newest first
   * @throws AutomationNotFoundException when an automation is named that the owner does not have
   */
  public List<AutomationRun> list(String ownerId, UUID automationId, Integer limit) {
    if (automationId != null) {
      automations
          .find(ownerId, automationId)
          .orElseThrow(() -> new AutomationNotFoundException(automationId));
    }
    int page = limit == null || limit < 1 ? DEFAULT_PAGE : Math.min(limit, MAX_PAGE);
    return runs.list(ownerId, automationId, page);
  }

  /**
   * One run with what started it.
   *
   * @param ownerId the owner
   * @param runId the run
   * @return the run
   * @throws RunNotFoundException when the owner has no such run
   */
  public RunDetail get(String ownerId, UUID runId) {
    return runs.find(ownerId, runId).orElseThrow(() -> new RunNotFoundException(runId));
  }
}
