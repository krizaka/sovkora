package com.krizaka.sovkora.persistence.domain.ports.inbound;

import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.jobs.domain.model.PageResult;
import com.krizaka.sovkora.persistence.domain.model.JobDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Public contract for job lifecycle management and state persistence. */
public interface JobPersistenceProvider {

  /**
   * Instantiates and persists a new job in PENDING status.
   *
   * @param userId The authenticated user ID.
   * @param featureKey The operation capability feature key.
   * @param payload The request parameters payload.
   * @param dataClass what the job's material is, declared by its producer; never defaulted
   *     (ADR-065)
   * @return The generated job ID.
   */
  String createJob(
      String userId, String featureKey, Map<String, Object> payload, DataClass dataClass);

  /**
   * Instantiates and persists a new job in PENDING status with a custom job ID.
   *
   * @param jobId The custom job ID to use, or null to auto-generate.
   * @param userId The authenticated user ID.
   * @param featureKey The operation capability feature key.
   * @param payload The request parameters payload.
   * @param dataClass what the job's material is, declared by its producer; never defaulted
   *     (ADR-065)
   * @return The job ID.
   */
  String createJob(
      String jobId,
      String userId,
      String featureKey,
      Map<String, Object> payload,
      DataClass dataClass);

  /**
   * Updates the execution status, result payload, or error of a job.
   *
   * @param jobId The target job ID.
   * @param status The new status (e.g. PROCESSING, COMPLETED, FAILED).
   * @param result The execution results.
   * @param errorMessage The failure reason if the job failed.
   */
  void updateJobStatus(
      String jobId, String status, Map<String, Object> result, String errorMessage);

  /**
   * Retrieves the current state snapshot of a job.
   *
   * @param jobId The job ID to resolve.
   * @return Optional containing the JobDto if found.
   */
  Optional<JobDto> getJob(String jobId);

  /**
   * Retrieves a paginated list of all jobs belonging to the authenticated user.
   *
   * @param userId The authenticated user ID.
   * @param pageable The pagination details.
   * @return Paginated list of JobDtos.
   */
  Page<JobDto> getJobsByUserId(String userId, Pageable pageable);

  /**
   * Retrieves a paginated list of all jobs across all users in the system.
   *
   * @param pageable The pagination details.
   * @return Paginated list of all JobDtos.
   */
  Page<JobDto> getAllJobs(Pageable pageable);

  /**
   * The newest-first page of one user's jobs, in plain Java: callers that must not depend on a
   * framework page type (sovkora-engine core) use this instead of {@link #getJobsByUserId}.
   */
  default PageResult<JobDto> pageJobsByUserId(String userId, int page, int size) {
    return toResult(getJobsByUserId(userId, newestFirst(page, size)));
  }

  /** The newest-first page of every user's jobs, in plain Java. */
  default PageResult<JobDto> pageAllJobs(int page, int size) {
    return toResult(getAllJobs(newestFirst(page, size)));
  }

  private static Pageable newestFirst(int page, int size) {
    return PageRequest.of(page, size, Sort.by("createdAt").descending());
  }

  private static PageResult<JobDto> toResult(Page<JobDto> page) {
    return new PageResult<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /**
   * Retrieves every job currently in one of the given statuses, across all users. Used by the
   * reconciler to recover jobs whose result landed on disk but whose status update was lost.
   *
   * @param statuses The statuses to match (e.g. PENDING, PROCESSING).
   * @return The matching jobs (unpaginated; intended for a bounded set of in-flight jobs).
   */
  List<JobDto> findJobsByStatuses(Collection<String> statuses);

  /**
   * Purges all job records associated with a specific user.
   *
   * @param userId The ID of the user whose jobs are to be deleted.
   */
  void purgeJobsByUserId(String userId);
}
