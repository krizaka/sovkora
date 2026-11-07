package com.krizaka.sovkora.core.application.security;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The actor a tool runs for. A tool is a plain function the model calls, so it cannot take the
 * actor as an argument; the executor runs it inside {@link #callAs}, and the tool asks {@link
 * #current()}. Outside a scope there is no actor, and a tool that needs one refuses: access is
 * closed by default.
 */
public final class ActorScope {

  private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

  private ActorScope() {}

  /** Runs {@code body} for {@code actor}, then restores whoever was current. */
  public static <T> T callAs(Actor actor, Supplier<T> body) {
    Actor previous = CURRENT.get();
    CURRENT.set(actor);
    try {
      return body.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  /** The actor of the running tool call, if any. */
  public static Optional<Actor> current() {
    return Optional.ofNullable(CURRENT.get());
  }
}
