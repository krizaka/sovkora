import { AutomationApi } from "@/services/automation.api";
import { restRequest } from "@/services/rest-client";

jest.mock("@/services/rest-client", () => ({ restRequest: jest.fn() }));
const rest = restRequest as jest.MockedFunction<typeof restRequest>;

const automation = {
  id: "a1",
  name: "Lease check",
  studioKey: "compliance-check",
  inputs: { jurisdiction: "QC" },
  enabled: true,
  createdAt: "2026-10-20T12:00:00Z",
  updatedAt: "2026-10-20T12:00:00Z",
};
const webhook = {
  id: "t1",
  automationId: "a1",
  kind: "webhook",
  enabled: true,
  webhookUrl: "https://www.orazaka.com/edge/hooks/t1",
  secretHint: "ab12",
  secret: ["whsec", "A".repeat(43) + "="].join("_"),
  catchUp: "once",
  createdAt: "2026-10-20T12:00:00Z",
};

describe("AutomationApi", () => {
  afterEach(() => jest.resetAllMocks());

  it("lists automations through the BFF proxy and parses them", async () => {
    rest.mockResolvedValue([automation]);
    const list = await AutomationApi.list();
    expect(rest).toHaveBeenCalledWith("/api/v1/automations");
    expect(list[0].studioKey).toBe("compliance-check");
  });

  it("treats an empty answer as an empty list", async () => {
    rest.mockResolvedValue(undefined);
    expect(await AutomationApi.list()).toEqual([]);
  });

  it("refuses a malformed row rather than rendering something it is not", async () => {
    rest.mockResolvedValue([{ id: "a1" }]);
    await expect(AutomationApi.list()).rejects.toThrow();
  });

  it("creates with a POST of the draft", async () => {
    rest.mockResolvedValue(automation);
    await AutomationApi.create({ name: "Lease check", studioKey: "compliance-check", inputs: {} });
    expect(rest).toHaveBeenCalledWith("/api/v1/automations", {
      method: "POST",
      body: { name: "Lease check", studioKey: "compliance-check", inputs: {} },
    });
  });

  it("enables or pauses with a PATCH, and removes with a DELETE", async () => {
    rest.mockResolvedValue(automation);
    await AutomationApi.update("a1", { enabled: false });
    expect(rest).toHaveBeenCalledWith("/api/v1/automations/a1", { method: "PATCH", body: { enabled: false } });
    rest.mockResolvedValue(undefined);
    await AutomationApi.remove("a1");
    expect(rest).toHaveBeenLastCalledWith("/api/v1/automations/a1", { method: "DELETE" });
  });

  it("adds a webhook trigger and returns its one-time secret", async () => {
    rest.mockResolvedValue(webhook);
    const trigger = await AutomationApi.addTrigger("a1", { kind: "webhook" });
    expect(rest).toHaveBeenCalledWith("/api/v1/automations/a1/triggers", { method: "POST", body: { kind: "webhook" } });
    expect(trigger.secret).toMatch(/^whsec_/);
  });

  it("rotates a secret by POST and removes a trigger by DELETE", async () => {
    rest.mockResolvedValue(webhook);
    await AutomationApi.rotateTrigger("a1", "t1");
    expect(rest).toHaveBeenCalledWith("/api/v1/automations/a1/triggers/t1/rotate", { method: "POST" });
    rest.mockResolvedValue(undefined);
    await AutomationApi.removeTrigger("a1", "t1");
    expect(rest).toHaveBeenLastCalledWith("/api/v1/automations/a1/triggers/t1", { method: "DELETE" });
  });

  it("escapes the ids it puts in a path", async () => {
    rest.mockResolvedValue([]);
    await AutomationApi.triggers("a/../b");
    expect(rest).toHaveBeenCalledWith("/api/v1/automations/a%2F..%2Fb/triggers");
  });

  it("reads the runs of an automation with a limit", async () => {
    rest.mockResolvedValue([
      { id: "r1", automationId: "a1", trigger: "webhook", status: "running", startedAt: "2026-10-20T12:00:01Z" },
    ]);
    const runs = await AutomationApi.runs("a1", 20);
    expect(rest).toHaveBeenCalledWith("/api/v1/automations/a1/runs?limit=20");
    expect(runs[0].status).toBe("running");
  });
});
