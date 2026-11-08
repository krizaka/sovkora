package com.krizaka.sovkora.studio.infrastructure.adapter.rest.dto;

import tools.jackson.databind.JsonNode;

/** A typed JSON replacement for one existing runtime setting. */
public record RuntimeConfigUpdateRequest(JsonNode value) {}
