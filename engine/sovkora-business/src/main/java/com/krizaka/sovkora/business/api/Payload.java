package com.krizaka.sovkora.business.api;

/**
 * Sealed hierarchy of the typed payloads an {@link Intention} can carry. Extends {@link
 * UseCasePayload} so a payload doubles as the input of the use-case that handles it. Exhaustive
 * {@code switch} over the permitted variants — no {@code instanceof} ladders.
 */
public sealed interface Payload extends UseCasePayload
    permits ChatPayload, ImagePayload, AgentPayload, StudioPayload {}
