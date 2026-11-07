package com.krizaka.sovkora.engine.persistence.api.domain.event;

import com.krizaka.sovkora.persistence.api.domain.model.JobDto;
import java.util.Objects;

/** Application event published when a job is created or its status changes. */
public record JobStatusChangedEvent(JobDto job) {
  public JobStatusChangedEvent {
    Objects.requireNonNull(job, "Job DTO cannot be null");
  }
}
