import { StudioApi } from "@/services/studio.api";
import { ConsentRequiredError } from "@/features/studio/consent/consent";

const installation = {
  id: "inst-1",
  studioKey: "guided-journaling",
  label: "Guided journaling",
  iconKey: "heart",
  pinnedVersion: "1.0.0",
  latestVersion: "1.0.0",
  status: "ACTIVE",
  config: {},
  installedAt: "2026-10-20T12:00:00Z",
  lastRunAt: null,
};

function answer(status: number, body: unknown) {
  global.fetch = jest.fn().mockResolvedValue({
    ok: status < 400,
    status,
    statusText: "",
    json: async () => body,
    text: async () => JSON.stringify(body),
  }) as unknown as typeof fetch;
}

describe("StudioApi.install and the consent a wellbeing Studio requires (451)", () => {
  it("surfaces the server's 451 as the consent step: version and statement, verbatim", async () => {
    answer(451, {
      status: "consent_required",
      message: "refused",
      studioKey: "guided-journaling",
      consentVersion: "1.0",
      statement: "Ce Studio enregistre ce que vous y écrivez.",
    });
    const failure = await StudioApi.install("guided-journaling", {}).catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ConsentRequiredError);
    expect((failure as ConsentRequiredError).requirement).toEqual({
      version: "1.0",
      statement: "Ce Studio enregistre ce que vous y écrivez.",
    });
  });

  it("leaves any other failure alone (a 451 without a statement is not a consent step)", async () => {
    answer(451, { status: "blocked" });
    await expect(StudioApi.install("guided-journaling", {})).rejects.not.toBeInstanceOf(ConsentRequiredError);
    answer(400, { status: "invalid_request", message: "installing guided-journaling is not available in region 'DE'" });
    await expect(StudioApi.install("guided-journaling", {})).rejects.toThrow("not available in region 'DE'");
  });

  it("sends the three declarations the server reads: consentVersion, ageAttested, region", async () => {
    answer(201, installation);
    await StudioApi.install("guided-journaling", {}, { consentVersion: "1.0", ageAttested: true, region: "CA-QC" });
    const [url, init] = jest.mocked(global.fetch).mock.calls[0];
    expect(url).toBe("/api/v1/studios/guided-journaling/installations");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      config: {},
      consentVersion: "1.0",
      ageAttested: true,
      region: "CA-QC",
    });
  });

  it("sends no consent block when none is given (a Studio that asks none stays as it was)", async () => {
    answer(201, installation);
    await StudioApi.install("image-generation", { tone: "formal" });
    const init = jest.mocked(global.fetch).mock.calls[0][1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({ config: { tone: "formal" } });
  });

  it("surfaces a 451 on a run as the consent step too (a statement that changed since)", async () => {
    answer(451, { status: "consent_required", consentVersion: "1.1", statement: "Nouvelle déclaration." });
    await expect(StudioApi.startRun("inst-1", {})).rejects.toBeInstanceOf(ConsentRequiredError);
  });

  it("asks for the statement in the person's language and carries the server's regions with their lines", async () => {
    const regions = [
      {
        region: "FR",
        label: "3114",
        contact: "3114",
        availability: "24h/24",
        sourceUrl: "https://3114.fr/",
        verifiedOn: "2026-10-20",
      },
    ];
    answer(451, {
      status: "consent_required",
      consentVersion: "2.0",
      statement: "This Studio records what you write.",
      regions,
    });
    const failure = await StudioApi.install("guided-journaling", {}, undefined, "en").catch((error: unknown) => error);
    expect(jest.mocked(global.fetch).mock.calls[0][0]).toBe(
      "/api/v1/studios/guided-journaling/installations?locale=en",
    );
    expect((failure as ConsentRequiredError).requirement).toEqual({
      version: "2.0",
      statement: "This Studio records what you write.",
      regions,
    });
  });

  it("erases all the wellbeing data with one DELETE on the pack, and one entry with a DELETE on the run", async () => {
    answer(200, { packKey: "bien-etre", entries: 2, installations: 1, jobs: 2 });
    await expect(StudioApi.eraseWellbeingData("bien-etre")).resolves.toEqual({
      packKey: "bien-etre",
      entries: 2,
      installations: 1,
      jobs: 2,
    });
    let [url, init] = jest.mocked(global.fetch).mock.calls[0];
    expect(url).toBe("/api/v1/studios/packs/bien-etre/my-data");
    expect((init as RequestInit).method).toBe("DELETE");

    answer(204, null);
    await StudioApi.eraseEntry("run-1");
    [url, init] = jest.mocked(global.fetch).mock.calls[0];
    expect(url).toBe("/api/v1/studios/runs/run-1");
    expect((init as RequestInit).method).toBe("DELETE");
  });
});
