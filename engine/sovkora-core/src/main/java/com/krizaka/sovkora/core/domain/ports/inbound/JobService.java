package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.job.JobInfo;
import com.krizaka.sovkora.core.domain.model.job.JobStatus;
import com.krizaka.sovkora.jobs.domain.model.DataClass;
import com.krizaka.sovkora.jobs.domain.model.PageResult;
import java.util.Map;
import java.util.Optional;

/**
 * Public inbound port for managing asynchronous tasks/jobs in the Sovkora ecosystem. Follows
 * ERR-105 (Interface-Driven Boundaries).
 */
public interface JobService {

  /**
   * Creates a new job with a random ID.
   *
   * @param userId User executing the job.
   * @param featureKey Bounded capability key.
   * @param payload Job parameters map.
   * @param dataClass what the job\'s material is, declared by its producer (ADR-065)
   * @return The generated Job ID.
   */
  String createJob(
      String userId, String featureKey, Map<String, Object> payload, DataClass dataClass);

  /**
   * Creates a new job with an explicit, predetermined ID.
   *
   * @param jobId Explicitly requested Job ID.
   * @param userId User executing the job.
   * @param featureKey Bounded capability key.
   * @param payload Job parameters map.
   * @param dataClass what the job\'s material is, declared by its producer (ADR-065)
   * @return The Job ID.
   */
  String createJob(
      String jobId,
      String userId,
      String featureKey,
      Map<String, Object> payload,
      DataClass dataClass);

  /**
   * Updates job status and saves results or error message.
   *
   * @param jobId Target Job ID.
   * @param status Next state (e.g. PROCESSING, COMPLETED, FAILED).
   * @param result Optional execution result payload.
   * @param errorMessage Optional failure reason message.
   */
  void updateJobStatus(
      String jobId, JobStatus status, Map<String, Object> result, String errorMessage);

  /**
   * Resolves a job by its unique ID.
   *
   * @param id Job ID.
   * @return Optional containing JobInfo if found.
   */
  Optional<JobInfo> getJob(String id);

  /**
   * Retrieves a paginated list of jobs owned by a specific user.
   *
   * @param userId Owner User ID.
   * @param page Zero-based page index.
   * @param size Page size.
   * @return Paginated job results, newest first.
   */
  PageResult<JobInfo> getJobsByUserId(String userId, int page, int size);

  /**
   * Retrieves a paginated list of all jobs across all users in the platform.
   *
   * @param page Zero-based page index.
   * @param size Page size.
   * @return Paginated job results, newest first.
   */
  PageResult<JobInfo> getAllJobs(int page, int size);

  /**
   * Purges all job records associated with a specific user.
   *
   * @param userId Target User ID.
   */
  void purgeJobsByUserId(String userId);
}
