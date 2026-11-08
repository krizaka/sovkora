import { edgeUrl } from "../config/api";
import { pipelineOfAck, stepOf } from "./pipeline";

/** Why a stream ended badly — the screen words it in the person's language. */
export type StreamFailure =
  | { readonly kind: "unauthorized" }
  | { readonly kind: "interrupted"; readonly status: number }
  | { readonly kind: "unreachable" };

interface StreamHandlers {
  /** Called with the full text so far, each time it grows. */
  readonly onContent: (accumulated: string) => void;
  /** The interceptor chain the router will run (from `pipeline-ack`), before the model answers. */
  readonly onPipeline?: (interceptorIds: readonly string[]) => void;
  /** One interceptor just finished (from `pipeline-step`). */
  readonly onStep?: (interceptorId: string) => void;
  readonly onDone: () => void;
  readonly onError: (failure: StreamFailure) => void;
}

/** How many times a request lost before its first byte is sent again, and the pause before each (it grows). */
const MAX_RETRIES = 2;
const RETRY_DELAY_MS = 300;
/** How long a request may stay silent before it is judged lost (the ack normally comes within milliseconds). */
const FIRST_BYTE_MS = 5000;

interface StreamOptions {
  /** A catalogue model name; absent or null: the platform's default. */
  readonly model?: string | null;
  /** The ids of the uploaded attachments this prompt refers to (images, files). */
  readonly assetIds?: readonly string[];
}

/** Parses one SSE `data:` payload that is JSON; `undefined` when it is not. */
function jsonOf(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return undefined;
  }
}

/**
 * Extracts the text out of one SSE `data:` payload.
 *
 * The server sends either a JSON object with `content`/`text`, or a bare string. Both shapes are
 * accepted here for the same reason the web client accepts both — the fallback is what keeps a
 * plain-text chunk from being dropped as unparseable.
 */
function chunkOf(raw: string): string {
  const parsed = jsonOf(raw);
  if (parsed === undefined) return raw;
  if (typeof parsed === "object" && parsed !== null) {
    const record = parsed as Record<string, unknown>;
    const text = record.content ?? record.text;
    return typeof text === "string" ? text : "";
  }
  return typeof parsed === "string" ? parsed : "";
}

/**
 * Streams a chat completion token by token.
 *
 * Built on `XMLHttpRequest`, not `fetch` or `EventSource`. React Native's `fetch` resolves only
 * when the whole body has arrived — it exposes no readable stream — so a token-by-token UI is
 * impossible through it, and `EventSource` neither exists natively nor can carry the
 * `Authorization` header the edge requires. XHR's incremental `responseText` is the one transport
 * that gives both. That is also why this reads the *delta* of `responseText` on each progress
 * event rather than re-parsing the whole buffer.
 *
 * @param conversationId the conversation to append to
 * @param prompt what the actor typed
 * @param token the bearer JWT
 * @param handlers content / pipeline / done / error callbacks
 * @param options the model to answer with
 * @returns a function that aborts the stream
 */
export function streamChat(
  conversationId: string,
  prompt: string,
  token: string | null,
  handlers: StreamHandlers,
  options: StreamOptions = {},
): () => void {
  let request = new XMLHttpRequest();
  let aborted = false;
  let retries = 0;
  let consumed = 0;
  let pending = "";
  let currentEvent = "message";
  let accumulated = "";
  /** The two timers a cancel must stop: the silence watchdog of the live attempt and the pause before a retry. */
  let silence: ReturnType<typeof setTimeout> | null = null;
  let retryPause: ReturnType<typeof setTimeout> | null = null;
  const clearSilence = () => {
    if (silence !== null) clearTimeout(silence);
    silence = null;
  };

  /** One line of the event stream: it names the event, or carries its data. */
  const handleLine = (line: string): void => {
    const cleaned = line.trim();
    if (cleaned === "") {
      currentEvent = "message";
      return;
    }
    if (cleaned.startsWith("event:")) {
      currentEvent = cleaned.slice(6).trim();
      return;
    }
    if (!cleaned.startsWith("data:")) return;
    const raw = cleaned.slice(5).trim();
    // pipeline-ack / pipeline-step describe the interceptor pipeline, not the answer: they feed the
    // pipeline chips and never land in the reply.
    if (currentEvent === "pipeline-ack") {
      const ids = pipelineOfAck(jsonOf(raw));
      if (ids.length > 0) handlers.onPipeline?.(ids);
      return;
    }
    if (currentEvent === "pipeline-step") {
      const id = stepOf(jsonOf(raw));
      if (id !== null) handlers.onStep?.(id);
      return;
    }
    if (currentEvent !== "message") return;
    const chunk = chunkOf(raw);
    if (chunk !== "") {
      accumulated += chunk;
      handlers.onContent(accumulated);
    }
  };

  const consume = (text: string) => {
    pending += text;
    const lines = pending.split("\n");
    // The last element may be half a line; keep it for the next progress event.
    pending = lines.pop() ?? "";
    for (const line of lines) handleLine(line);
  };

  const attach = (): void => {
    const mine = request;
    /** This attempt was replaced by a newer one: whatever it still reports is ignored. */
    const stale = (): boolean => mine !== request || aborted;
    const retry = (): boolean => {
      if (retries >= MAX_RETRIES || stale() || consumed > 0) return false;
      retries += 1;
      mine.abort();
      // The new request replaces this one at once (whatever this one still reports is ignored); a short pause lets
      // the dead connection be torn down before the new one opens a fresh one.
      request = new XMLHttpRequest();
      retryPause = setTimeout(() => {
        retryPause = null;
        if (!aborted) attach();
      }, RETRY_DELAY_MS);
      return true;
    };

    mine.open("POST", edgeUrl(`/api/v1/chat/stream/${conversationId}`));
    mine.setRequestHeader("Content-Type", "application/json");
    mine.setRequestHeader("Accept", "text/event-stream");
    if (token !== null) {
      mine.setRequestHeader("Authorization", `Bearer ${token}`);
    }

    mine.onreadystatechange = () => {
      if (stale()) return;
      // 3 = LOADING: the body is still arriving, which is the whole point.
      if (mine.readyState >= 3) {
        const text = mine.responseText;
        if (text.length > consumed) {
          clearSilence();
          consume(text.slice(consumed));
          consumed = text.length;
        }
      }
      if (mine.readyState === 4) {
        clearSilence();
        if (mine.status >= 200 && mine.status < 300) {
          handlers.onDone();
        } else {
          handlers.onError(
            mine.status === 401 ? { kind: "unauthorized" } : { kind: "interrupted", status: mine.status },
          );
        }
      }
    };

    // iOS reuses an idle HTTP/3 connection that the edge has already dropped and waits ~6 s before reporting "The
    // network connection was lost"; it never retries a POST. The router acknowledges a request within milliseconds
    // (`pipeline-ack`), so a request that has heard nothing after FIRST_BYTE_MS is on a dead connection: nothing was
    // received, so sending it again on a fresh one is safe — the difference between an answer and a false error.
    mine.onerror = () => {
      if (stale()) return;
      clearSilence();
      if (!retry()) handlers.onError({ kind: "unreachable" });
    };
    mine.send(JSON.stringify({ prompt, assetIds: options.assetIds ?? [], model: options.model ?? null }));
    silence = setTimeout(() => {
      if (consumed === 0) retry();
    }, FIRST_BYTE_MS);
  };
  attach();

  return () => {
    aborted = true;
    clearSilence();
    if (retryPause !== null) clearTimeout(retryPause);
    retryPause = null;
    request.abort();
  };
}
