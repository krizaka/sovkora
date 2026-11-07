package com.krizaka.sovkora.business.api;

/**
 * Marker for a typed input accepted by a {@link UseCase}. Every {@link Payload} carried by an
 * {@link Intention} is a {@code UseCasePayload}, so the dispatcher can hand the intention's payload
 * straight to the resolved use-case.
 */
public interface UseCasePayload {}
