import { pipelineProgress } from "@/features/chat-session/components/ThinkingPipeline";

describe("pipelineProgress", () => {
  it("has nothing to show without a pipeline", () => {
    expect(pipelineProgress(undefined, new Set())).toEqual({
      steps: [],
      total: 0,
      doneCount: 0,
      allDone: true,
      current: null,
    });
  });

  const schema = {
    coreInterceptorIds: ["AuthInterceptor", "SemanticRouterInterceptor"],
    dynamicInterceptorIds: ["PiiGuardInterceptor"],
  };

  it("shows the first step when nothing is done", () => {
    const p = pipelineProgress(schema as never, new Set());
    expect(p.total).toBe(3);
    expect(p.doneCount).toBe(0);
    expect(p.allDone).toBe(false);
    expect(p.current?.id).toBe("AuthInterceptor");
  });

  it("moves to the first unfinished step, even when a later one finished first", () => {
    const p = pipelineProgress(schema as never, new Set(["AuthInterceptor", "PiiGuardInterceptor"]));
    expect(p.doneCount).toBe(2);
    expect(p.allDone).toBe(false);
    expect(p.current?.id).toBe("SemanticRouterInterceptor");
    expect(p.current?.phase).toBe("core");
  });

  it("ends on the last step, dynamic phase, once the whole chain is done", () => {
    const p = pipelineProgress(
      schema as never,
      new Set(["AuthInterceptor", "SemanticRouterInterceptor", "PiiGuardInterceptor"]),
    );
    expect(p.allDone).toBe(true);
    expect(p.doneCount).toBe(3);
    expect(p.current).toMatchObject({ id: "PiiGuardInterceptor", phase: "dynamic", label: "Pii Guard" });
  });

  it("ignores finished ids that are not in the pipeline", () => {
    const p = pipelineProgress(schema as never, new Set(["Unknown"]));
    expect(p.doneCount).toBe(0);
    expect(p.current?.id).toBe("AuthInterceptor");
  });

  it("has no current step for an empty pipeline", () => {
    const p = pipelineProgress({ coreInterceptorIds: [], dynamicInterceptorIds: [] } as never, new Set());
    expect(p).toMatchObject({ total: 0, current: null, allDone: true });
  });
});
