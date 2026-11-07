package com.krizaka.sovkora.business.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for a {@link Capability#STUDIO} intention — one run of an installed Studio (ADR-034).
 *
 * <p>Carries the installation rather than the Studio key: the pinned blueprint version and the
 * actor's configuration both live on the installation, and resolving them here would duplicate a
 * decision the Studio context owns.
 *
 * <p>Deliberately a <b>contract-copy</b>: it uses only JDK types rather than depending on {@code
 * sovkora-studio-api}, exactly as {@code BillableCapability} contract-copies {@link Capability} in
 * the opposite direction. Whether {@code business} takes that dependency is a phase-3 decision,
 * made when the DAG interpreter actually lands here.
 *
 * @param installationId The installation to run (required).
 * @param inputs The actor's answers to the blueprint's input schema, validated server-side against
 *     that schema before any credit is held; never null.
 */
public record StudioPayload(UUID installationId, Map<String, Object> inputs) implements Payload {
  public StudioPayload {
    Objects.requireNonNull(installationId, "installationId must not be null");
    inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
  }
}
