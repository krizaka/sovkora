import { birdFor } from "@/components/mascot/birdFor";

describe("birdFor", () => {
  it("gives each kind of work its bird", () => {
    expect(birdFor("compliance-check")).toBe("owl");
    expect(birdFor("document-authenticity")).toBe("owl");
    expect(birdFor("image-generation")).toBe("duck");
    expect(birdFor("orazaka.core.media.speech")).toBe("duck");
    expect(birdFor("lead-research")).toBe("pigeon");
    expect(birdFor("mood-tracking")).toBe("flamingo");
    expect(birdFor(undefined)).toBe("owl");
  });
});
