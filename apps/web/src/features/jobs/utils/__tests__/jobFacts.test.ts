import {
  factOf,
  formatDuration,
  gpuName,
  jobFacts,
  modelOf,
  WORKER_KEYS,
  whereRan,
  wallTimeMs,
} from "@/features/jobs/utils/jobFacts";
import type { Job } from "@/core/types/jobs.types";

const job = (over: Partial<Job> = {}): Job => ({
  id: "j1",
  userId: "u",
  featureKey: "orazaka.core.media.video",
  status: "COMPLETED",
  payload: {},
  result: {},
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:02:47Z",
  ...over,
});

describe("every key the worker emits has a label", () => {
  it.each(WORKER_KEYS)("%s", (key) => expect(factOf(key)).not.toBeNull());
});

describe("wall time", () => {
  it("is queue plus run, not the API call", () => {
    expect(wallTimeMs(job())).toBe(167_000);
    expect(formatDuration(167_000)).toBe("2 min 47 s");
    expect(formatDuration(768)).toBe("768 ms");
    expect(formatDuration(12_400)).toBe("12.4 s");
  });
  it("is unknown while the job runs unless the caller gives its clock", () => {
    expect(wallTimeMs(job({ status: "PROCESSING" }))).toBeNull();
    expect(wallTimeMs(job({ status: "PROCESSING" }), Date.parse("2026-10-16T10:00:30Z"))).toBe(30_000);
  });
});

describe("jobFacts", () => {
  const video = job({
    payload: { model: "wan2.2-ti2v-5b", seed: 42, steps: 30, width: 1280, height: 704, frames: 121, fps: 24 },
    result: {
      format: "mp4",
      durationMs: 768,
      metrics: {
        gpu: "NVIDIA H100 80GB HBM3",
        provider: "runpod",
        gpu_allocated_mb: 29800,
        peak_memory_rss_mb: 4096,
        inference_time_sec: 167.7,
        aspect: "16:9",
      },
    },
  });
  const byId = Object.fromEntries(jobFacts(video).map((f) => [f.id, f]));

  it("groups and formats with units, never raw keys", () => {
    expect(byId.resolution).toMatchObject({ section: "output", value: "1280×704" });
    expect(byId.clipLength.value).toBe("5.0 s");
    expect(byId.fps.value).toBe("24 fps");
    expect(byId.format.value).toBe("MP4");
    expect(byId.vram).toMatchObject({ section: "hardware", value: "29.1 GB" });
    expect(byId.peakRam.value).toBe("4.0 GB");
    expect(byId.inference).toMatchObject({ section: "timing", value: "167.7 s" });
    expect(byId.total.value).toBe("2 min 47 s");
    expect(byId.apiCall.value).toBe("768 ms");
    expect(jobFacts(video).some((f) => f.id.includes("_"))).toBe(false);
  });
  it("gives an unknown key a label in words, in its own section", () => {
    const f = jobFacts(job({ result: { metrics: { peakQueueDepth: 3 } } })).find((x) => x.section === "other")!;
    expect(f).toMatchObject({ label: "Peak queue depth", value: "3" });
  });
});

describe("model and where it ran", () => {
  it("names the model from the registry, or as the job says", () => {
    expect(modelOf(job({ payload: { model: "something-custom" } }))).toBe("something-custom");
    expect(modelOf(job())).toBeNull();
    expect(modelOf(job({ result: { metadata: { model: "x-y" } } }))).toBe("x-y");
  });
  it("says where: GPU and provider, Mac, a provider alone", () => {
    expect(whereRan(job({ result: { metrics: { gpu: "NVIDIA H100 80GB HBM3", provider: "runpod" } } }))).toBe(
      "H100 · RunPod",
    );
    expect(whereRan(job({ result: { metrics: { gpu: "Apple M3 Max", provider: "mlx" } } }))).toBe("Mac · Metal");
    expect(whereRan(job({ payload: { provider: "digitalocean" } }))).toBe("DigitalOcean");
    expect(whereRan(job())).toBeNull();
    expect(gpuName("NVIDIA GeForce RTX 4090")).toBe("RTX 4090");
  });
});
