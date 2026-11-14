package com.krizaka.sovkora.job.infrastructure.adapter.rest.dto;

import java.util.List;

/**
 * The pods whose token a call revoked.
 *
 * @param revoked their keys
 */
public record PodRevocationResponse(List<String> revoked) {}
