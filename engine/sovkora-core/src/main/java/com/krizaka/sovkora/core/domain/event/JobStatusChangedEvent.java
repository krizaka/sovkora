package com.krizaka.sovkora.core.domain.event;

import com.krizaka.sovkora.core.domain.model.job.JobInfo;
import java.util.Objects;

/** Core application event published when a job is created or its status changes. */
public record JobStatusChangedEvent(JobInfo job) {
  public JobStatusChangedEvent {
    Objects.requireNonNull(job, "JobInfo cannot be null");
  }
}
