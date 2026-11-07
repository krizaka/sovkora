import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import { PROOF_TOOL, RECORDED_QUESTION } from "./tour/eric.mjs";

describe("the recorded chat proves a server tool run (orazaka-ai-engine#169)", () => {
  it("asks for a tool already registered on dev (the knowledge search), by what the tool does", () => {
    assert.equal(PROOF_TOOL, "searchKnowledge");
    assert.match(RECORDED_QUESTION, /search our knowledge base/);
    assert.doesNotMatch(RECORDED_QUESTION, /statute lookup/); // the law tool is not registered on dev (orazaka.law.lookup.enabled)
  });
  it("keeps the event stream beside the video", () => {
    const showcase = readFileSync(new URL("./showcase-web.mjs", import.meta.url), "utf8");
    assert.match(showcase, /text\/event-stream/);
    assert.match(showcase, /chat-sse\.txt/);
  });
});
