package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.tools.application.service.LawWatchService;
import com.krizaka.sovkora.tools.domain.model.law.LawWatchReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** The scheduled run of the law watcher. Logs counts only, never article content. */
public class LawWatchJob {

  private static final Logger LOG = LoggerFactory.getLogger(LawWatchJob.class);

  private final LawWatchService service;

  /**
   * Creates the job.
   *
   * @param service the watcher
   */
  public LawWatchJob(LawWatchService service) {
    this.service = service;
  }

  /** One watch, on the schedule of {@code orazaka.law.watcher.cron}. */
  @Scheduled(cron = "${orazaka.law.watcher.cron:0 0 6 * * MON}", zone = "UTC")
  public void run() {
    LawWatchReport report = service.run();
    LOG.info(
        "law watch done: imports={} unchanged={} stored={} requests={} failures={}",
        report.imports().size(),
        report.unchanged(),
        report.stored(),
        report.requests().size(),
        report.failures().size());
  }
}
