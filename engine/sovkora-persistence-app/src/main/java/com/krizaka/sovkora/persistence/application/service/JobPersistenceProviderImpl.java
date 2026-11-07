package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.persistence.domain.event.JobStatusChangedEvent;
import com.krizaka.sovkora.persistence.domain.model.JobDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.JobPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.JobEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.JobRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of the JobPersistenceProvider. */
@Service
@Transactional
class JobPersistenceProviderImpl implements JobPersistenceProvider {

  /** A deleted job is a tombstone: it is never listed. */
  private static final String DELETED = "DELETED";

  private final JobRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  JobPersistenceProviderImpl(JobRepository repository, ApplicationEventPublisher eventPublisher) {
    this.repository = Objects.requireNonNull(repository, "JobRepository cannot be null");
    this.eventPublisher =
        Objects.requireNonNull(eventPublisher, "ApplicationEventPublisher cannot be null");
  }

  @Override
  public String createJob(
      String userId, String featureKey, Map<String, Object> payload, DataClass dataClass) {
    return createJob(null, userId, featureKey, payload, dataClass);
  }

  @Override
  public String createJob(
      String jobId,
      String userId,
      String featureKey,
      Map<String, Object> payload,
      DataClass dataClass) {
    Objects.requireNonNull(featureKey, "Feature key cannot be null");
    // No default: a job whose producer declared no class would keep its material under no window
    // at all (ADR-065).
    Objects.requireNonNull(dataClass, "a job must declare its data class");
    String id = jobId == null || jobId.isBlank() ? UUID.randomUUID().toString() : jobId;
    JobEntity entity = new JobEntity();
    entity.setId(id);
    entity.setUserId(userId);
    entity.setFeatureKey(featureKey);
    entity.setStatus("PENDING");
    entity.setPayload(payload);
    entity.setDataClass(dataClass.name());

    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    repository.save(entity);
    eventPublisher.publishEvent(new JobStatusChangedEvent(toDto(entity)));
    return id;
  }

  @Override
  public void updateJobStatus(
      String jobId, String status, Map<String, Object> result, String errorMessage) {
    Objects.requireNonNull(jobId, "Job ID cannot be null");
    Objects.requireNonNull(status, "Status cannot be null");

    JobEntity entity =
        repository
            .findById(jobId)
            .orElseThrow(
                () -> new IllegalArgumentException("Job with ID " + jobId + " does not exist"));

    entity.setStatus(status);
    if (result != null) {
      entity.setResult(result);
    }
    entity.setErrorMessage(errorMessage);
    entity.setUpdatedAt(Instant.now());

    repository.save(entity);
    eventPublisher.publishEvent(new JobStatusChangedEvent(toDto(entity)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<JobDto> getJob(String jobId) {
    Objects.requireNonNull(jobId, "Job ID cannot be null");
    return repository.findById(jobId).map(JobPersistenceProviderImpl::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<JobDto> getJobsByUserId(String userId, Pageable pageable) {
    Objects.requireNonNull(userId, "User ID cannot be null");
    Objects.requireNonNull(pageable, "Pageable cannot be null");
    return repository
        .findByUserIdAndStatusNot(userId, DELETED, pageable)
        .map(JobPersistenceProviderImpl::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<JobDto> getAllJobs(Pageable pageable) {
    Objects.requireNonNull(pageable, "Pageable cannot be null");
    return repository.findByStatusNot(DELETED, pageable).map(JobPersistenceProviderImpl::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobDto> findJobsByStatuses(Collection<String> statuses) {
    Objects.requireNonNull(statuses, "Statuses cannot be null");
    if (statuses.isEmpty()) {
      return List.of();
    }
    return repository.findByStatusIn(statuses).stream()
        .map(JobPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public void purgeJobsByUserId(String userId) {
    Objects.requireNonNull(userId, "User ID cannot be null");
    repository.deleteByUserId(userId);
  }

  private static JobDto toDto(JobEntity entity) {
    if (entity == null) {
      return null;
    }
    return new JobDto(
        entity.getId(),
        entity.getUserId(),
        entity.getFeatureKey(),
        entity.getStatus(),
        entity.getPayload(),
        entity.getResult(),
        entity.getErrorMessage(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
