import { JobSchema } from "@krizaka/orazaka-shared";
import { jobArtefacts } from "../jobArtefacts";

/** Jobs as the dev job service returned them on 2026-10-22 (Mira's account), ids shortened. */
const job = (featureKey: string, result: Record<string, unknown> | null, status = "COMPLETED") =>
  JobSchema.parse({
    id: "j1",
    userId: "u1",
    featureKey,
    status,
    payload: {},
    result,
    createdAt: "2026-10-20T10:00:00Z",
    updatedAt: "2026-10-20T10:01:00Z",
  });

describe("what a job made, as the run screen's artefacts", () => {
  it("an image job: the picture, then its facts", () => {
    const a = jobArtefacts(
      job("orazaka.core.media.image", {
        model: "flux-2-klein-4b-q8",
        url: "/api/v1/assets/5943/image.png",
        seed: "2026",
        generationMs: 77000,
        format: "png",
      }),
    );
    expect(a.map((x) => [x.key, x.type])).toEqual([
      ["result", "IMAGE"],
      ["model", "TEXT"],
      ["seed", "TEXT"],
      ["generationMs", "TEXT"],
    ]);
    expect(a[0]!.value).toBe("/api/v1/assets/5943/image.png");
  });

  it("a voice job: the audio, typed by its format", () => {
    expect(
      jobArtefacts(
        job("orazaka.core.media.speech", { format: "mp3", url: "/api/v1/assets/58c7/speech.mp3", durationMs: 4200 }),
      ).map((x) => x.type),
    ).toEqual(["AUDIO", "TEXT"]);
  });

  it("without a format: the URL's extension, else the job's kind", () => {
    expect(jobArtefacts(job("orazaka.core.media.video", { url: "https://cdn.example/clip.mp4?sig=1" }))[0]!.type).toBe(
      "VIDEO",
    );
    expect(jobArtefacts(job("orazaka.core.media.image", { url: "/api/v1/assets/9/out" }))[0]!.type).toBe("IMAGE");
  });

  it("a text job: its content", () => {
    expect(jobArtefacts(job("orazaka.core.chat.completion", { content: "Bonjour." }))).toEqual([
      { key: "content", label: "content", type: "TEXT", value: "Bonjour." },
    ]);
  });

  it("nothing readable, or a failed job: nothing shown, never a guess", () => {
    expect(jobArtefacts(job("orazaka.core.media.image", null, "FAILED"))).toEqual([]);
    expect(jobArtefacts(job("orazaka.core.chat.completion", { content: "   " }))).toEqual([]);
  });
});
