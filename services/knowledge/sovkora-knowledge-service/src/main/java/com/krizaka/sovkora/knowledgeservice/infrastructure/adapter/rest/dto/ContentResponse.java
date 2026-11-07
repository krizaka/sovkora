package com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto;

/**
 * Uniform response of the knowledge read surface: the resolved content (possibly empty), never
 * null.
 *
 * @param content the retrieved context or joined source content
 */
public record ContentResponse(String content) {}
