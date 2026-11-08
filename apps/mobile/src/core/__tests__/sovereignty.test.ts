import { answeringModel, chatModels, staysOnNetwork, type CatalogModel } from "../sovereignty";
import { humanizeInterceptor, pipelineOfAck, stepOf } from "../pipeline";

const catalog: CatalogModel[] = [
  { modelName: "qwen2.5:7b", category: "chat", providerName: "ollama" },
  {
    modelName: "mistral-3-14B",
    modelLabel: "Mistral 3 14B",
    category: "chat",
    providerName: "do-inference",
    isDefault: true,
  },
  { modelName: "wan2-2-t2v-a14b", category: "video", providerName: "do-inference" },
  { modelName: "legacy", category: "chat", providerName: "ollama", compatible: false },
];

describe("what may be claimed about the network", () => {
  it("lists only the compatible chat models, the default first", () => {
    expect(chatModels(catalog).map((m) => m.modelName)).toEqual(["mistral-3-14B", "qwen2.5:7b"]);
  });

  it("answers with the picked model, else the catalogue's default", () => {
    expect(answeringModel(catalog, "qwen2.5:7b")?.modelName).toBe("qwen2.5:7b");
    expect(answeringModel(catalog, null)?.modelName).toBe("mistral-3-14B");
    expect(answeringModel(catalog, "gone")?.modelName).toBe("mistral-3-14B");
  });

  it("says nothing stays on the network when the default model is a hosted inference provider", () => {
    // The dev catalogue's default is a hosted model: the "0 bytes left your network" line must not appear.
    expect(staysOnNetwork(answeringModel(catalog, null))).toBe(false);
  });

  it("says it only for a self-hosted provider", () => {
    expect(staysOnNetwork(answeringModel(catalog, "qwen2.5:7b"))).toBe(true);
  });

  it("says nothing when the model is unknown", () => {
    expect(staysOnNetwork(null)).toBe(false);
    expect(staysOnNetwork(answeringModel([], null))).toBe(false);
  });
});

describe("the pipeline of a request", () => {
  it("lists core then dynamic interceptors from the ack", () => {
    expect(pipelineOfAck({ coreInterceptorIds: ["A"], dynamicInterceptorIds: ["B", "C"] })).toEqual(["A", "B", "C"]);
  });

  it("is empty for anything that is not an ack", () => {
    expect(pipelineOfAck(null)).toEqual([]);
    expect(pipelineOfAck("x")).toEqual([]);
    expect(pipelineOfAck({ coreInterceptorIds: [1] })).toEqual([]);
  });

  it("reads the step id", () => {
    expect(stepOf({ interceptorId: "RouterInterceptor" })).toBe("RouterInterceptor");
    expect(stepOf({})).toBeNull();
  });

  it("spaces an interceptor id nobody has named yet", () => {
    expect(humanizeInterceptor("SemanticRouterInterceptor")).toBe("Semantic Router");
    expect(humanizeInterceptor("RAGLookupInterceptor")).toBe("RAG Lookup");
  });
});
