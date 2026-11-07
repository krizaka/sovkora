package com.krizaka.sovkora.business.usecases.studio;

import com.krizaka.sovkora.business.api.*;
import com.krizaka.sovkora.studio.domain.port.StudioRunClient;
import java.util.Objects;
import java.util.Set;

/**
 * Runs an installed Studio as a first-class {@code Intention} (ADR-034 §9.2).
 *
 * <p>This is the point of having an {@code Intention} abstraction at all: a Studio run becomes
 * reachable from the intent controller, the CLI and the agent loop alike, rather than only from the
 * Studio service's own REST surface. Not routing it through here would have made the marketplace a
 * web-only feature.
 *
 * <p>The use-case is deliberately <b>thin</b>. {@code business} coordinates and hosts no engine
 * (AGENTS.md §2): the DAG interpreter lives in the studio service, reached through the Tier-1
 * {@link StudioRunClient} port whose HTTP adapter is supplied by {@code sovkora-studio-client}.
 * That indirection is what keeps this library free of a dependency on another context's Tier-3
 * implementation, which SEAM-002 forbids.
 *
 * <p>{@link PlanningMode#DETERMINISTIC}: the plan is the blueprint, decided by an admin at publish
 * time. Nothing here asks a model what to do next.
 */
public final class StudioRunUseCase implements UseCase<StudioPayload, String> {

  private static final UseCaseDescriptor DESCRIPTOR =
      new UseCaseDescriptor(
          "studio.run",
          Capability.STUDIO,
          Set.of(),
          PlanningMode.DETERMINISTIC,
          Set.of(),
          RbacPolicy.PERMIT_ALL);

  private final StudioRunClient studioRunClient;

  public StudioRunUseCase(StudioRunClient studioRunClient) {
    this.studioRunClient = Objects.requireNonNull(studioRunClient, "studioRunClient must not null");
  }

  @Override
  public UseCaseDescriptor descriptor() {
    return DESCRIPTOR;
  }

  /**
   * Accepts a run and returns its id.
   *
   * <p>An id, not a result: a run is a saga of durable jobs that can last minutes, and its progress
   * reaches the caller over the job plane's existing SSE relay. A use-case that blocked for the
   * outcome would hold a request open for the length of a video render.
   *
   * @param ctx the intention's context
   * @param input the installation to run and the actor's answers
   * @return the accepted run's id
   */
  @Override
  public String execute(UseCaseContext ctx, StudioPayload input) {
    return studioRunClient.start(input.installationId(), input.inputs());
  }
}
