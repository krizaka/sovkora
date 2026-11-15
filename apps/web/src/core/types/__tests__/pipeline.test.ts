import {
  humanizeInterceptor,
  isPipelineSchema,
  toPipelineSteps,
  type ChatPipelineSchema,
} from "@/core/types/pipeline.types";

describe("humanizeInterceptor", () => {
  test("strips the Interceptor suffix and splits camelCase", () => {
    expect(humanizeInterceptor("SemanticRouterInterceptor")).toBe("Semantic Router");
    expect(humanizeInterceptor("TranslationInterceptor")).toBe("Translation");
    expect(humanizeInterceptor("ClosedLoopValidationInterceptor")).toBe("Closed Loop Validation");
    expect(humanizeInterceptor("HTTPServerInterceptor")).toBe("HTTP Server");
  });

  test("stays linear on a long run of capitals", () => {
    const started = performance.now();
    humanizeInterceptor("A".repeat(100_000));
    expect(performance.now() - started).toBeLessThan(1000);
  });
});

describe("isPipelineSchema", () => {
  test("accepts an object with coreInterceptorIds array", () => {
    expect(isPipelineSchema({ pipelineId: "default", coreInterceptorIds: [] })).toBe(true);
  });

  test("rejects chat content payloads and non-objects", () => {
    expect(isPipelineSchema({ content: "hello" })).toBe(false);
    expect(isPipelineSchema(null)).toBe(false);
    expect(isPipelineSchema("x")).toBe(false);
  });
});

describe("toPipelineSteps", () => {
  test("flattens core then dynamic, with phase + humanized labels", () => {
    const schema: ChatPipelineSchema = {
      pipelineId: "default",
      coreInterceptorIds: ["SecurityInterceptor"],
      dynamicInterceptorIds: ["TranslationInterceptor"],
      estimatedLatencyMs: 10,
    };
    expect(toPipelineSteps(schema)).toEqual([
      { id: "SecurityInterceptor", label: "Security", phase: "core" },
      { id: "TranslationInterceptor", label: "Translation", phase: "dynamic" },
    ]);
  });
});
