package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import com.krizaka.sovkora.conversation.infrastructure.adapter.rest.dto.IntentionRequest;
import com.krizaka.sovkora.engine.business.api.*;
import com.krizaka.sovkora.engine.core.domain.model.Context;
import com.krizaka.users.domain.model.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for the {@code intent} resource.
 *
 * <p>The App-Factory ingress: an authenticated intention, dispatched at the collection root.
 */
@RestController
@RequestMapping("/api/v1/intent")
public class IntentController {

  private static final Logger logger = LoggerFactory.getLogger(IntentController.class);

  private final UseCaseDispatcher dispatcher;

  public IntentController(UseCaseDispatcher dispatcher) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "UseCaseDispatcher must not be null");
  }

  /** Dispatches a synchronous user intention through the App Factory and returns the result. */
  @PostMapping
  public ResponseEntity<Object> submit(
      @RequestBody IntentionRequest request, @AuthenticationPrincipal User user) {
    Intention intention = toIntention(request, user);
    try {
      Object result = dispatcher.dispatch(intention);
      return ResponseEntity.ok(result);
    } catch (UseCaseResolutionException e) {
      logger.warn("Use case resolution failed for user={}: {}", user.id(), e.getMessage(), e);
      boolean denied = e.getMessage() != null && e.getMessage().contains("ERR-403");
      throw new ResponseStatusException(
          denied ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private static Intention toIntention(IntentionRequest request, User user) {
    Capability capability;
    try {
      capability = Capability.valueOf(request.capability().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      logger.warn("Unknown capability: {}", request.capability(), e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "unknown capability: " + request.capability());
    }

    Payload payload =
        switch (capability) {
          case CHAT -> new ChatPayload(request.prompt(), List.of());
          case IMAGE -> new ImagePayload(request.prompt(), request.size());
          case AGENT ->
              new AgentPayload(
                  request.goal() != null ? request.goal() : request.prompt(), Map.of());
            // A Studio run is a first-class Intention (ADR-034 §9.2): the same marketplace the web
            // client uses is reachable from here, the CLI and the agent loop alike.
          case STUDIO ->
              new StudioPayload(UUID.fromString(request.installationId()), request.inputs());
          case AUDIO, VIDEO, ADMIN ->
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST,
                  "capability not yet wired through the App Factory: " + capability);
        };

    IntentionType type =
        capability == Capability.CHAT ? IntentionType.QUERY : IntentionType.COMMAND;
    IntentionContext context =
        new IntentionContext(
            request.sessionId(),
            user != null ? user.id().toString() : null,
            user != null ? user.authorities() : Set.of(),
            // The user's own namespace, as every other Context builder does (ADR-064).
            user != null ? Context.userPreferences(user.preferences()) : Map.of());

    return new Intention(
        null, type, ExecutionMode.SYNC, capability, request.goal(), payload, context);
  }
}
