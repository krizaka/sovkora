package com.krizaka.sovkora.core.domain.event;

import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import java.util.Objects;

/**
 * Immutable Spring Application Event emitted when an Sovkora chat request completes successfully.
 *
 * <p>Carries the turn's credit reservation so the settlement listener can close it against the
 * tokens the model actually produced (ADR-033 §6.2). The hold is taken in the interceptor pipeline
 * and settled after inference; this event is the only place both halves are in scope at once.
 *
 * @param request The original request.
 * @param response The generated response, including what the turn consumed.
 * @param holdId The credit reservation for this turn, or {@code null} when it was not metered.
 */
public record ChatCompletedEvent(
    InternalChatRequest request, InternalChatResponse response, String holdId) {

  public ChatCompletedEvent {
    Objects.requireNonNull(request, "Request cannot be null");
    Objects.requireNonNull(response, "Response cannot be null");
  }

  /**
   * Overload for the paths that never took a hold — tests and unmetered flows.
   *
   * @param request The original request.
   * @param response The generated response.
   */
  public ChatCompletedEvent(InternalChatRequest request, InternalChatResponse response) {
    this(request, response, null);
  }

  /**
   * Whether this turn can be settled at all.
   *
   * @return {@code true} only when a reservation was taken — a turn with no hold was never
   *     authorised through billing and must not have one invented at settlement time
   */
  public boolean metered() {
    return holdId != null && !holdId.isBlank();
  }
}
