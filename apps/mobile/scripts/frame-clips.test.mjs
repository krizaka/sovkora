import assert from "node:assert/strict";
import test from "node:test";
import { CAPTIONS, vtt } from "./frame-clips.mjs";

test("every clip has EN and FR captions, in order, inside the clip", () => {
  for (const [id, cues] of Object.entries(CAPTIONS)) {
    let last = 0;
    for (const [a, b, en, fr] of cues) {
      assert.ok(a >= last && b > a && b <= 1, id);
      assert.ok(en.length > 0 && fr.length > 0 && en !== fr, id);
      last = b;
    }
  }
});

test("WebVTT: header, timestamps from the duration", () => {
  const out = vtt([[0, 0.5, "A", "B"]], 20, "fr");
  assert.match(out, /^WEBVTT\n\n1\n00:00:00\.000 --> 00:00:10\.000\nB\n/);
});
