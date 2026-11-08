import { streamChat, type StreamFailure } from "../streamChat";

/**
 * A controllable stand-in for the XHR the streamer drives.
 *
 * Hand-rolled rather than mocked with a library because the whole point of these tests is the
 * *incremental* delivery: the fake must be able to grow `responseText` one chunk at a time, which
 * is exactly the behaviour a request-level mock would paper over.
 */
class FakeXhr {
  static instances: FakeXhr[] = [];

  readyState = 0;
  status = 0;
  responseText = "";
  onreadystatechange: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly headers: Record<string, string> = {};
  sentBody: string | null = null;
  aborted = false;

  constructor() {
    FakeXhr.instances.push(this);
  }

  open(): void {
    this.readyState = 1;
  }

  setRequestHeader(name: string, value: string): void {
    this.headers[name] = value;
  }

  send(body: string): void {
    this.sentBody = body;
  }

  abort(): void {
    this.aborted = true;
  }

  /** Appends to the body and fires a progress event, as a real streaming response does. */
  push(chunk: string): void {
    this.readyState = 3;
    this.responseText += chunk;
    this.onreadystatechange?.();
  }

  /** Completes the request. */
  finish(status = 200): void {
    this.readyState = 4;
    this.status = status;
    this.onreadystatechange?.();
  }
}

beforeEach(() => {
  jest.useFakeTimers();
  FakeXhr.instances = [];
  (globalThis as { XMLHttpRequest: unknown }).XMLHttpRequest = FakeXhr;
});

function start(
  token: string | null = "jwt-123",
  options: { model?: string | null; assetIds?: readonly string[] } = {},
) {
  const content: string[] = [];
  const errors: StreamFailure[] = [];
  const pipeline: string[][] = [];
  const steps: string[] = [];
  let done = false;

  const abort = streamChat(
    "conv-1",
    "Bonjour",
    token,
    {
      onContent: (accumulated) => content.push(accumulated),
      onPipeline: (ids) => pipeline.push([...ids]),
      onStep: (id) => steps.push(id),
      onDone: () => {
        done = true;
      },
      onError: (failure) => errors.push(failure),
    },
    options,
  );

  return { xhr: FakeXhr.instances[0], content, errors, pipeline, steps, abort, isDone: () => done };
}

describe("streamChat", () => {
  it("carries the bearer token so the edge does not answer 401", () => {
    const { xhr } = start();

    expect(xhr.headers.Authorization).toBe("Bearer jwt-123");
    expect(xhr.headers.Accept).toBe("text/event-stream");
  });

  it("omits the header entirely when signed out rather than sending 'Bearer null'", () => {
    const { xhr } = start(null);

    expect(xhr.headers.Authorization).toBeUndefined();
  });

  it("accumulates tokens, emitting the whole text so far each time", () => {
    const { xhr, content } = start();

    xhr.push('data: {"content":"Bon"}\n\n');
    xhr.push('data: {"content":"jour"}\n\n');

    expect(content).toEqual(["Bon", "Bonjour"]);
  });

  it("holds a half-delivered line until its remainder arrives", () => {
    const { xhr, content } = start();

    // A chunk boundary in the middle of the JSON — the normal case over a real socket, and the
    // one that silently truncates an answer if the buffer is parsed eagerly.
    xhr.push('data: {"cont');
    expect(content).toEqual([]);

    xhr.push('ent":"Bonjour"}\n\n');
    expect(content).toEqual(["Bonjour"]);
  });

  it("reads a bare string payload, not only the JSON envelope", () => {
    const { xhr, content } = start();

    xhr.push("data: Bonjour\n\n");

    expect(content).toEqual(["Bonjour"]);
  });

  it("ignores pipeline events so interceptor traffic never lands in the reply", () => {
    const { xhr, content } = start();

    xhr.push('event: pipeline-ack\ndata: {"coreInterceptorIds":["a"],"dynamicInterceptorIds":["b"]}\n\n');
    xhr.push('event: pipeline-step\ndata: {"interceptorId":"translation"}\n\n');
    xhr.push('data: {"content":"Bonjour"}\n\n');

    expect(content).toEqual(["Bonjour"]);
  });

  it("reports the real pipeline: the chain from the ack, then each step as it finishes", () => {
    const { xhr, pipeline, steps } = start();

    xhr.push(
      'event:pipeline-ack\ndata:{"pipelineId":"default","coreInterceptorIds":["UserContextResolver","SafetyInterceptor"],"dynamicInterceptorIds":["RouterInterceptor"],"estimatedLatencyMs":70}\n\n',
    );
    xhr.push('event:pipeline-step\ndata:{"interceptorId":"UserContextResolver"}\n\n');
    xhr.push('event:pipeline-step\ndata:{"interceptorId":"SafetyInterceptor"}\n\n');

    expect(pipeline).toEqual([["UserContextResolver", "SafetyInterceptor", "RouterInterceptor"]]);
    expect(steps).toEqual(["UserContextResolver", "SafetyInterceptor"]);
  });

  it("ignores a malformed pipeline payload instead of failing the answer", () => {
    const { xhr, pipeline, content } = start();

    xhr.push("event:pipeline-ack\ndata:not-json\n\n");
    xhr.push('data:{"content":"ok"}\n\n');

    expect(pipeline).toEqual([]);
    expect(content).toEqual(["ok"]);
  });

  it("resumes reading message events after a pipeline event", () => {
    const { xhr, content } = start();

    // The blank line ends the pipeline event; without resetting, everything after it would be
    // discarded as if it were still pipeline traffic — an answer that never appears.
    xhr.push('event: pipeline-step\ndata: {"interceptorId":"x"}\n\n');
    xhr.push('data: {"content":"Salut"}\n\n');

    expect(content).toEqual(["Salut"]);
  });

  it("reports the end of a successful stream", () => {
    const { xhr, isDone, errors } = start();

    xhr.push('data: {"content":"ok"}\n\n');
    xhr.finish(200);

    expect(isDone()).toBe(true);
    expect(errors).toEqual([]);
  });

  it("names an expired session rather than blaming the network", () => {
    const { xhr, errors } = start();

    xhr.finish(401);

    expect(errors[0]).toEqual({ kind: "unauthorized" });
  });

  it("reports other failures with their status", () => {
    const { xhr, errors } = start();

    xhr.finish(503);

    expect(errors[0]).toEqual({ kind: "interrupted", status: 503 });
  });

  it("sends the request once more when the connection is lost before anything arrived", () => {
    const { content, errors } = start();

    FakeXhr.instances[0]?.onerror?.();
    jest.advanceTimersByTime(400);
    expect(FakeXhr.instances).toHaveLength(2);
    expect(errors).toEqual([]);

    FakeXhr.instances[1]?.push('data: {"content":"ok"}\n\n');
    expect(content).toEqual(["ok"]);
  });

  it("says the platform is unreachable when every attempt fails", () => {
    const { errors } = start();

    FakeXhr.instances[0]?.onerror?.();
    jest.advanceTimersByTime(400);
    FakeXhr.instances[1]?.onerror?.();
    jest.advanceTimersByTime(400);
    FakeXhr.instances[2]?.onerror?.();

    expect(errors).toEqual([{ kind: "unreachable" }]);
    expect(FakeXhr.instances).toHaveLength(3);
  });

  it("judges a request that stays silent on a dead connection lost, and sends it again", () => {
    const { content } = start();

    jest.advanceTimersByTime(5100);
    expect(FakeXhr.instances[0]?.aborted).toBe(true);
    jest.advanceTimersByTime(400);
    expect(FakeXhr.instances).toHaveLength(2);

    // Whatever the abandoned request reports later is ignored.
    FakeXhr.instances[0]?.push('data: {"content":"ghost"}\n\n');
    FakeXhr.instances[1]?.push('data: {"content":"real"}\n\n');
    expect(content).toEqual(["real"]);
  });

  it("does not time out a request whose acknowledgement arrived", () => {
    const { xhr } = start();

    xhr.push('event:pipeline-ack\ndata:{"coreInterceptorIds":["a"]}\n\n');
    jest.advanceTimersByTime(20000);

    expect(FakeXhr.instances).toHaveLength(1);
    expect(xhr.aborted).toBe(false);
  });

  it("does not resend once words have arrived — the answer would be duplicated", () => {
    const { xhr, errors } = start();

    xhr.push('data: {"content":"Bon"}\n\n');
    xhr.onerror?.();

    expect(FakeXhr.instances).toHaveLength(1);
    expect(errors).toEqual([{ kind: "unreachable" }]);
  });

  it("aborts the underlying request when the caller stops listening", () => {
    const { xhr, abort } = start();

    abort();

    expect(xhr.aborted).toBe(true);
  });

  it("sends the model the person picked", () => {
    expect(JSON.parse(start("jwt", { model: "gemma-4-31B-it" }).xhr.sentBody ?? "{}")).toMatchObject({
      model: "gemma-4-31B-it",
    });
  });

  it("sends null for the platform default", () => {
    expect(JSON.parse(start("jwt").xhr.sentBody ?? "{}")).toMatchObject({ model: null });
  });

  it("sends the prompt the actor typed", () => {
    const { xhr } = start();

    expect(JSON.parse(xhr.sentBody ?? "{}")).toMatchObject({ prompt: "Bonjour" });
  });

  // orazaka-mobile-client#68
  it("sends the attachments the prompt refers to instead of an empty list", () => {
    const { xhr } = start("jwt-123", { assetIds: ["a-1", "a-2"] });

    expect(JSON.parse(xhr.sentBody ?? "{}")).toMatchObject({ assetIds: ["a-1", "a-2"] });
  });

  it("sends an empty attachment list when there is none", () => {
    const { xhr } = start();

    expect(JSON.parse(xhr.sentBody ?? "{}")).toMatchObject({ assetIds: [] });
  });

  it("a cancel before the first byte leaves no timer behind: no retry fires afterwards", () => {
    const { abort } = start();

    abort();
    jest.advanceTimersByTime(60_000);

    expect(FakeXhr.instances).toHaveLength(1);
    expect(jest.getTimerCount()).toBe(0);
  });

  it("a cancel during the pause before a retry stops the retry", () => {
    const { xhr, abort } = start();
    xhr.onerror?.();
    abort();
    jest.advanceTimersByTime(60_000);

    // The replacement request was created by the retry, but it is never opened or sent.
    expect(FakeXhr.instances.filter((instance) => instance.sentBody !== null)).toHaveLength(1);
    expect(jest.getTimerCount()).toBe(0);
  });
});
