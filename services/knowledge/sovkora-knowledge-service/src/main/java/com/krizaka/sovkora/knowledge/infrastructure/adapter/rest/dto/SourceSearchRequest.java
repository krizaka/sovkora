package com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto;

/**
 * Request for a tool RAG-source substring search. It names no user, tenant or role: who is asking
 * comes from the verified token, never from the body (ADR-091).
 *
 * @param toolId the tool whose sources to search
 * @param query the search text; blank returns every readable source for the tool
 */
public record SourceSearchRequest(String toolId, String query) {}
