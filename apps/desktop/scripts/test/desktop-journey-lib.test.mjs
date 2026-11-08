import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import { IDENTIFIER, minimalPdf, modelReply, scopeFile, scopeJson, startModel } from "../desktop-journey-lib.mjs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("the Mac journey's fixtures", () => {
  it("keeps the scope where the app does (its identifier in tauri.conf.json)", () => {
    assert.equal(JSON.parse(read("../../src-tauri/tauri.conf.json")).identifier, IDENTIFIER);
    assert.equal(scopeFile("/Users/x"), "/Users/x/Library/Application Support/com.krizaka.orazaka.desktop/scope.json");
    assert.deepEqual(JSON.parse(scopeJson(["/a"])), { roots: ["/a"] });
  });
  it("writes a PDF whose xref offsets point at its objects", () => {
    const pdf = minimalPdf("Quarterly report (draft)").toString("latin1");
    assert.match(pdf, /^%PDF-1\.4/);
    assert.match(pdf, /\(Quarterly report draft\) Tj/);
    const start = Number(/startxref\n(\d+)/.exec(pdf)[1]);
    assert.ok(pdf.slice(start).startsWith("xref"));
    const offsets = [...pdf.slice(start).matchAll(/(\d{10}) 00000 n/g)].map((m) => Number(m[1]));
    offsets.forEach((o, i) => assert.ok(pdf.slice(o).startsWith(`${i + 1} 0 obj`)));
  });
  it("answers like an OpenAI-compatible server, and only to those two routes", () => {
    assert.deepEqual(modelReply("GET", "/v1/models", "s").data, [{ id: "journey-model" }]);
    assert.equal(modelReply("POST", "/v1/chat/completions", "the summary").choices[0].message.content, "the summary");
    assert.equal(modelReply("GET", "/v1/other", "s"), undefined);
  });
  it("serves them on loopback", async () => {
    const model = await startModel("hello");
    try {
      assert.match(model.baseUrl, /^http:\/\/127\.0\.0\.1:\d+\/v1$/);
      const models = await (await fetch(`${model.baseUrl}/models`)).json();
      assert.equal(models.data[0].id, "journey-model");
      const chat = await (await fetch(`${model.baseUrl}/chat/completions`, { method: "POST", body: "{}" })).json();
      assert.equal(chat.choices[0].message.content, "hello");
    } finally {
      await model.close();
    }
  });
});

describe("the embedded WebDriver never reaches a release", () => {
  it("is an optional feature, off by default, and compiled only in debug builds", () => {
    const cargo = read("../../src-tauri/Cargo.toml");
    assert.match(cargo, /tauri-plugin-webdriver = \{[^}]*optional = true/);
    assert.doesNotMatch(cargo, /^default\s*=/m);
    assert.match(read("../../src-tauri/src/lib.rs"), /#\[cfg\(all\(debug_assertions, feature = "webdriver"\)\)\]/);
    assert.doesNotMatch(read("../../package.json"), /build:app[^\n]*webdriver/);
  });
  it("films only through the shared tooling, never by screen recording", () => {
    const journey = read("../desktop-journey.mjs");
    assert.match(journey, /KRIZAKA_FILM_DIR/);
    assert.doesNotMatch(journey.replace(/^\s*\/\/.*$/gm, ""), /screencapture/);
    assert.match(read("../../../../.github/workflows/desktop-film.yml"), /krizaka-desktop\/\.github\/actions\/film@[0-9a-f]{40}/);
  });
});
