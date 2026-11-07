package com.krizaka.sovkora.persistence.application.service;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.persistence.domain.model.JobDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.JobEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unit tests for {@link JobPersistenceProviderImpl}. */
@ExtendWith(MockitoExtension.class)
class JobPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private JobRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private JobPersistenceProviderImpl provider;

  @BeforeEach
  void setUp() {
    provider = new JobPersistenceProviderImpl(repository, eventPublisher);
  }

  private static JobEntity buildEntity(String id, String userId, String featureKey, String status) {
    Instant now = Instant.now(FIXED_CLOCK);
    JobEntity entity = new JobEntity();
    entity.setId(id);
    entity.setUserId(userId);
    entity.setFeatureKey(featureKey);
    entity.setStatus(status);
    entity.setPayload(Map.of());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  @Test
  void createJob_withNullId_generatesUuid() {
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    String id =
        provider.createJob(null, USER_1, IMAGE, Map.of("prompt", "test"), DataClass.STANDARD);

    assertNotNull(id);
    assertFalse(id.isBlank());
    verify(repository).save(any(JobEntity.class));
    verify(eventPublisher).publishEvent(any(Object.class));
  }

  @Test
  void createJob_withExplicitId_usesProvidedId() {
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    String id = provider.createJob("my-id", USER_1, "speech", Map.of(), DataClass.SENSITIVE);

    assertEquals("my-id", id);
  }

  @Test
  void createJob_noIdOverload_delegatesToFull() {
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    String id = provider.createJob(USER_1, IMAGE, Map.of(), DataClass.STANDARD);

    assertNotNull(id);
  }

  @Test
  void createJob_persistsTheDeclaredClass() {
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    provider.createJob("sensitive-id", USER_1, "speech", Map.of(), DataClass.SENSITIVE);

    org.mockito.ArgumentCaptor<JobEntity> saved =
        org.mockito.ArgumentCaptor.forClass(JobEntity.class);
    verify(repository).save(saved.capture());
    assertEquals("SENSITIVE", saved.getValue().getDataClass());
  }

  @Test
  void createJob_withoutAClass_isRefusedNotDefaulted() {
    Map<String, Object> payload = Map.of();
    NullPointerException refused =
        assertThrows(
            NullPointerException.class, () -> provider.createJob(USER_1, IMAGE, payload, null));
    assertTrue(refused.getMessage().contains("data class"));
    verifyNoInteractions(repository);
  }

  @Test
  void createJob_nullFeatureKey_throwsNpe() {
    Map<String, Object> payload = Map.of();
    assertThrows(
        NullPointerException.class,
        () -> provider.createJob(USER_1, null, payload, DataClass.STANDARD));
  }

  @Test
  void updateJobStatus_existingJob_updatesFields() {
    JobEntity entity = buildEntity(JOB_1, USER_1, IMAGE, "PENDING");
    when(repository.findById(JOB_1)).thenReturn(Optional.of(entity));
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    provider.updateJobStatus(JOB_1, STATUS_COMPLETED, Map.of(URL, "/result"), null);

    assertEquals(STATUS_COMPLETED, entity.getStatus());
    assertEquals("/result", entity.getResult().get(URL));
    verify(eventPublisher).publishEvent(any(Object.class));
  }

  @Test
  void updateJobStatus_nonexistentJob_throwsException() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> provider.updateJobStatus("nope", STATUS_COMPLETED, null, null));
  }

  @Test
  void updateJobStatus_nullJobId_throwsNpe() {
    assertThrows(
        NullPointerException.class, () -> provider.updateJobStatus(null, "OK", null, null));
  }

  @Test
  void updateJobStatus_nullStatus_throwsNpe() {
    assertThrows(
        NullPointerException.class, () -> provider.updateJobStatus("id", null, null, null));
  }

  @Test
  void updateJobStatus_withErrorMessage_setsError() {
    JobEntity entity = buildEntity("job-err", USER_1, IMAGE, "RUNNING");
    when(repository.findById("job-err")).thenReturn(Optional.of(entity));
    when(repository.save(any(JobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    provider.updateJobStatus("job-err", "FAILED", null, "Timeout exceeded");

    assertEquals("FAILED", entity.getStatus());
    assertEquals("Timeout exceeded", entity.getErrorMessage());
  }

  @Test
  void getJob_existing_returnsDto() {
    JobEntity entity = buildEntity(JOB_1, USER_1, IMAGE, STATUS_COMPLETED);
    entity.setResult(Map.of(URL, "/img.png"));
    when(repository.findById(JOB_1)).thenReturn(Optional.of(entity));

    Optional<JobDto> result = provider.getJob(JOB_1);

    assertTrue(result.isPresent());
    assertEquals(JOB_1, result.get().id());
    assertEquals(STATUS_COMPLETED, result.get().status());
  }

  @Test
  void getJob_nonexistent_returnsEmpty() {
    when(repository.findById("none")).thenReturn(Optional.empty());

    assertTrue(provider.getJob("none").isEmpty());
  }

  @Test
  void getJob_nullId_throwsNpe() {
    assertThrows(NullPointerException.class, () -> provider.getJob(null));
  }

  @Test
  void getJobsByUserId_returnsPage() {
    JobEntity entity = buildEntity("j1", USER_1, IMAGE, "PENDING");
    Pageable pageable = PageRequest.of(0, 10);
    when(repository.findByUserIdAndStatusNot(USER_1, "DELETED", pageable))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<JobDto> result = provider.getJobsByUserId(USER_1, pageable);

    assertEquals(1, result.getTotalElements());
  }

  @Test
  void getAllJobs_returnsPage() {
    Pageable pageable = PageRequest.of(0, 20);
    when(repository.findByStatusNot("DELETED", pageable)).thenReturn(new PageImpl<>(List.of()));

    Page<JobDto> result = provider.getAllJobs(pageable);

    assertEquals(0, result.getTotalElements());
  }

  @Test
  void purgeJobsByUserId_delegatesToRepository() {
    provider.purgeJobsByUserId(USER_1);

    verify(repository).deleteByUserId(USER_1);
  }

  @Test
  void purgeJobsByUserId_nullUserId_throwsNpe() {
    assertThrows(NullPointerException.class, () -> provider.purgeJobsByUserId(null));
  }

  @Test
  void getJobsByUserId_neverListsADeletedJob() {
    Pageable pageable = PageRequest.of(0, 10);
    JobEntity kept = buildEntity("kept", USER_1, IMAGE, "COMPLETED");
    when(repository.findByUserIdAndStatusNot(USER_1, "DELETED", pageable))
        .thenReturn(new PageImpl<>(List.of(kept), pageable, 1));

    Page<JobDto> page = provider.getJobsByUserId(USER_1, pageable);

    assertEquals(1, page.getTotalElements());
    assertEquals("kept", page.getContent().getFirst().id());
    verify(repository, never()).findByUserId(any(), any());
  }

  @Test
  void getAllJobs_neverListsADeletedJob() {
    Pageable pageable = PageRequest.of(0, 10);
    when(repository.findByStatusNot("DELETED", pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    assertEquals(0, provider.getAllJobs(pageable).getTotalElements());
    verify(repository, never()).findAll(any(Pageable.class));
  }
}
