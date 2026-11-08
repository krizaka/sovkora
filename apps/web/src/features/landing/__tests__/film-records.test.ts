import fs from "node:fs";
import path from "node:path";
import { filmFacts, NO_FILM_FACTS } from "@/features/landing/utils/film-records";

const shot = (runId: string, gpuCostUsd: number, gpu = "NVIDIA H200") => ({
  id: runId,
  runId,
  model: "m",
  gpu,
  gpuSeconds: 1,
  gpuCostUsd,
});

describe("filmFacts: the run facts are summed from the records, never typed", () => {
  it("counts runs, sums the GPU cost, lists the cards and the run ids", () => {
    const facts = filmFacts({ models: [{ role: "shots", name: "Wan" }], shots: [shot("a", 0.25), shot("b", 0.255)] });
    expect(facts).toMatchObject({ runs: 2, gpuCostUsd: 0.51, gpus: ["NVIDIA H200"], runIds: ["a", "b"] });
    expect(facts.models).toEqual([{ role: "shots", name: "Wan" }]);
  });

  it("shows nothing it cannot verify: unreadable records give no figure", () => {
    expect(filmFacts(null)).toEqual(NO_FILM_FACTS);
    expect(filmFacts({ shots: [{ runId: "x" }, { runId: 3 }] }).gpuCostUsd).toBeNull();
    expect(filmFacts({ shots: [shot("a", Number.NaN)] }).runs).toBe(0);
  });

  it("the committed records are complete: seven kept shots, one H200 card, three models", () => {
    const raw = JSON.parse(
      fs.readFileSync(path.join(process.cwd(), "src/features/landing/utils/film.records.json"), "utf8"),
    );
    const facts = filmFacts(raw);
    expect(facts.runs).toBe(7);
    expect(facts.gpus).toEqual(["NVIDIA H200"]);
    expect(facts.models.map((m) => m.role)).toEqual(["keyframes", "shots", "music"]);
    expect(new Set(facts.runIds).size).toBe(7);
  });
});
