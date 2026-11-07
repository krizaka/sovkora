package com.krizaka.sovkora.core.application.service;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.event.JobStatusChangedEvent;
import com.krizaka.sovkora.core.domain.model.job.JobInfo;
import com.krizaka.sovkora.core.domain.model.job.JobStatus;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.jobs.domain.model.PageResult;
import com.krizaka.sovkora.persistence.domain.model.JobDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.JobPersistenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultJobServiceTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private JobPersistenceProvider persistenceProvider;
  private DomainEventPublisher eventPublisher;
  private DefaultJobService jobService;

  @BeforeEach
  void setUp() {
    persistenceProvider = mock(JobPersistenceProvider.class);
    eventPublisher = mock(DomainEventPublisher.class);
    jobService = new DefaultJobService(persistenceProvider, eventPublisher);
  }

  @Test
  @DisplayName("constructor rejects null dependencies")
  void constructorValidation() {
    assertThrows(NullPointerException.class, () -> new DefaultJobService(null, eventPublisher));
    assertThrows(
        NullPointerException.class, () -> new DefaultJobService(persistenceProvider, null));
  }

  @Test
  @DisplayName("create job forwards calls and publishes event")
  void createJob() {
    String jobId = "job-123";
    String userId = "user-456";
    String featureKey = FEAT;
    Map<String, Object> payload = Map.of("key", "val");

    when(persistenceProvider.createJob(userId, featureKey, payload, DataClass.SENSITIVE))
        .thenReturn(jobId);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, userId, featureKey, STATUS_PENDING, payload, Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    String returnedId = jobService.createJob(userId, featureKey, payload, DataClass.SENSITIVE);

    assertEquals(jobId, returnedId);
    verify(persistenceProvider).createJob(userId, featureKey, payload, DataClass.SENSITIVE);

    ArgumentCaptor<JobStatusChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(JobStatusChangedEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    assertEquals(jobId, eventCaptor.getValue().job().id());
  }

  @Test
  @DisplayName("create job with custom ID forwards calls and publishes event")
  void createJobWithId() {
    String jobId = "job-123";
    String userId = "user-456";
    String featureKey = FEAT;
    Map<String, Object> payload = Map.of("key", "val");

    when(persistenceProvider.createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE))
        .thenReturn(jobId);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, userId, featureKey, STATUS_PENDING, payload, Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    String returnedId =
        jobService.createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE);

    assertEquals(jobId, returnedId);
    verify(persistenceProvider).createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE);
  }

  @Test
  @DisplayName("update job status forwards calls and publishes event")
  void updateJobStatus() {
    String jobId = "job-123";
    JobStatus status = JobStatus.COMPLETED;
    Map<String, Object> result = Map.of("done", true);
    String error = null;

    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, USER_DASH_1, FEAT, status.name(), Map.of(), result, error, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    jobService.updateJobStatus(jobId, status, result, error);

    verify(persistenceProvider).updateJobStatus(jobId, status.name(), result, error);
    verify(eventPublisher).publish(any(JobStatusChangedEvent.class));
  }

  @Test
  @DisplayName("get job returns mapped result")
  void getJob() {
    String jobId = "job-123";
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.empty());
    assertTrue(jobService.getJob(jobId).isEmpty());

    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, USER_DASH_1, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    Optional<JobInfo> res = jobService.getJob(jobId);
    assertTrue(res.isPresent());
    assertEquals(jobId, res.get().id());
  }

  @Test
  @DisplayName("get jobs by user ID returns mapped page")
  void getJobsByUserId() {
    String userId = USER_DASH_1;
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(JOB_1, userId, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    PageResult<JobDto> page = new PageResult<>(List.of(dto), 0, 10, 1);
    when(persistenceProvider.pageJobsByUserId(userId, 0, 10)).thenReturn(page);

    PageResult<JobInfo> result = jobService.getJobsByUserId(userId, 0, 10);
    assertNotNull(result);
    assertEquals(1, result.totalElements());
    assertEquals(JOB_1, result.content().getFirst().id());
  }

  @Test
  @DisplayName("get all jobs returns mapped page")
  void getAllJobs() {
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(JOB_1, USER_DASH_1, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    PageResult<JobDto> page = new PageResult<>(List.of(dto), 0, 10, 1);
    when(persistenceProvider.pageAllJobs(0, 10)).thenReturn(page);

    PageResult<JobInfo> result = jobService.getAllJobs(0, 10);
    assertNotNull(result);
    assertEquals(1, result.totalElements());
    assertEquals(JOB_1, result.content().getFirst().id());
  }

  @Test
  @DisplayName("purge jobs forwards to provider")
  void purgeJobs() {
    String userId = USER_DASH_1;
    jobService.purgeJobsByUserId(userId);
    verify(persistenceProvider, times(1)).purgeJobsByUserId(userId);
  }
}
