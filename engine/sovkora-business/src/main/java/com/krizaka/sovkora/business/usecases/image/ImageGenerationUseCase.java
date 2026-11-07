package com.krizaka.sovkora.business.usecases.image;

import com.krizaka.sovkora.business.api.*;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Second reference use-case — image generation. Its sole reason to exist is to prove the App
 * Factory's extensibility: adding a new product capability is a new {@code UseCase} class
 * registered with a {@code @Bean} line, and the {@link
 * com.krizaka.sovkora.business.api.UseCaseRegistry} routes intentions to it by {@link Capability} —
 * no core or router change.
 */
public final class ImageGenerationUseCase implements UseCase<ImagePayload, ImageResponse> {

  private static final UseCaseDescriptor DESCRIPTOR =
      new UseCaseDescriptor(
          "image.generate",
          Capability.IMAGE,
          Set.of(),
          PlanningMode.DETERMINISTIC,
          Set.of(),
          RbacPolicy.PERMIT_ALL);

  private final AiClient aiClient;

  public ImageGenerationUseCase(AiClient aiClient) {
    this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null");
  }

  @Override
  public UseCaseDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ImageResponse execute(UseCaseContext ctx, ImagePayload input) {
    Context coreContext =
        new Context(
            ctx.actorId() != null ? ctx.actorId() : "anonymous",
            ctx.sessionId() != null ? ctx.sessionId() : ctx.intentionId(),
            ctx.preferences(),
            Set.of());
    // Width/height/model resolve from the catalog defaults downstream; size hint travels as a
    // setting so the provider can honour it when present.
    Map<String, Object> settings = input.size() != null ? Map.of("size", input.size()) : Map.of();
    return aiClient.image(
        new ImageRequest(input.prompt(), null, null, null, settings, coreContext));
  }
}
