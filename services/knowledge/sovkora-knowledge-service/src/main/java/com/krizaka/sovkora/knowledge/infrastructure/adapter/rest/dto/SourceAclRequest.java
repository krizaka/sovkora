package com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto;

import java.util.List;

/**
 * The new access list of a source.
 *
 * @param principals {@code role:<ROLE>}, {@code group:<GROUP>} or {@code user:<id>} entries; empty
 *     means the owner alone
 */
public record SourceAclRequest(List<String> principals) {}
