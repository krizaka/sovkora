import { parseRouting, routingStatus } from "@/core/utils/routing";

describe("parseRouting", () => {
  it("accepts a complete event and rounds the credits", () => {
    expect(parseRouting({ provider: "ollama", local: true, estimatedCredits: 29.6 })).toEqual({
      provider: "ollama",
      local: true,
      estimatedCredits: 30,
    });
  });
  it("refuses anything that does not say where it ran", () => {
    expect(parseRouting(null)).toBeNull();
    expect(parseRouting({ provider: "ollama" })).toBeNull();
    expect(parseRouting({ provider: "", local: true })).toBeNull();
    expect(parseRouting({ provider: "x", local: "yes" })).toBeNull();
  });
  it("drops a nonsensical estimate rather than showing it", () => {
    expect(parseRouting({ provider: "x", local: false, estimatedCredits: -3 })).toEqual({
      provider: "x",
      local: false,
    });
  });
});

describe("routingStatus", () => {
  it("claims nothing without a routing", () => {
    expect(routingStatus(null)).toEqual({ localOnly: false, routedTo: null, credits: null });
  });
  it("claims local only when the last routing was local", () => {
    expect(routingStatus({ provider: "ollama", local: true, estimatedCredits: 30 })).toEqual({
      localOnly: true,
      routedTo: null,
      credits: 30,
    });
  });
  it("names the provider, neutrally, when the answer left the network", () => {
    expect(routingStatus({ provider: "openai", local: false })).toEqual({
      localOnly: false,
      routedTo: "openai",
      credits: null,
    });
  });
});
