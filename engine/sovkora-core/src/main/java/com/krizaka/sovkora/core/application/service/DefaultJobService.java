package com.krizaka.sovkora.core.application.service;

import com.krizaka.sovkora.core.domain.event.JobStatusChangedEvent;
import com.krizaka.sovkora.core.domain.model.job.JobInfo;
import com.krizaka.sovkora.core.domain.model.job.JobStatus;
import com.krizaka.sovkora.core.domain.ports.inbound.JobService;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.jobs.domain.model.PageResult;
import com.krizaka.sovkora.persistence.domain.model.JobDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.JobPersistenceProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of the JobService inbound port, plain Java (wired by the Spring adapter).
 * Delegates to the out-of-boundary persistence package. Follows ERR-105 (Interface-Driven
 * Boundaries).
 */
public class DefaultJobService implements JobService {

  private final JobPersistenceProvider jobPersistenceProvider;
  private final DomainEventPublisher eventPublisher;

  public DefaultJobService(
      JobPersistenceProvider jobPersistenceProvider, DomainEventPublisher eventPublisher) {
    this.jobPersistenceProvider =
        Objects.requireNonNull(jobPersistenceProvider, "JobPersistenceProvider cannot be null");
    this.eventPublisher =
        Objects.requireNonNull(eventPublisher, "DomainEventPublisher cannot be null");
  }

  @Override
  public String createJob(
      String userId, String featureKey, Map<String, Object> payload, DataClass dataClass) {
    String jobId = jobPersistenceProvider.createJob(userId, featureKey, payload, dataClass);
    publishEvent(jobId);
    return jobId;
  }

  @Override
  public String createJob(
      String jobId,
      String userId,
      String featureKey,
      Map<String, Object> payload,
      DataClass dataClass) {
    String createdId =
        jobPersistenceProvider.createJob(jobId, userId, featureKey, payload, dataClass);
    publishEvent(createdId);
    return createdId;
  }

  @Override
  public void updateJobStatus(
      String jobId, JobStatus status, Map<String, Object> result, String errorMessage) {
    jobPersistenceProvider.updateJobStatus(jobId, status.name(), result, errorMessage);
    publishEvent(jobId);
  }

  @Override
  public Optional<JobInfo> getJob(String id) {
    return jobPersistenceProvider.getJob(id).map(DefaultJobService::toInfo);
  }

  @Override
  public PageResult<JobInfo> getJobsByUserId(String userId, int page, int size) {
    return jobPersistenceProvider
        .pageJobsByUserId(userId, page, size)
        .map(DefaultJobService::toInfo);
  }

  @Override
  public PageResult<JobInfo> getAllJobs(int page, int size) {
    return jobPersistenceProvider.pageAllJobs(page, size).map(DefaultJobService::toInfo);
  }

  @Override
  public void purgeJobsByUserId(String userId) {
    jobPersistenceProvider.purgeJobsByUserId(userId);
  }

  private void publishEvent(String jobId) {
    getJob(jobId).ifPresent(jobInfo -> eventPublisher.publish(new JobStatusChangedEvent(jobInfo)));
  }

  private static JobInfo toInfo(JobDto dto) {
    return new JobInfo(
        dto.id(),
        dto.userId(),
        dto.featureKey(),
        JobStatus.fromString(dto.status()),
        dto.payload(),
        dto.result(),
        dto.errorMessage(),
        dto.createdAt(),
        dto.updatedAt());
  }
}
