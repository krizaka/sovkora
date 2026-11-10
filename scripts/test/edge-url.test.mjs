// node --test scripts/test — the tools reach the edge only on its own origin and under its own path.
import assert from "node:assert/strict";
import { test } from "node:test";
import { edgeBase, edgeUrl, idSegment, oneLine } from "../lib/edge-url.mjs";

const base = edgeBase({});

test("the default edge, an https one from the environment, and a local http one are accepted", () => {
  assert.equal(base.href, "https://dev.sovkora.com/edge");
  assert.equal(edgeBase({ ORAZAKA_EDGE_URL: "https://dev.sovkora.com/edge/" }).href, "https://dev.sovkora.com/edge");
  assert.equal(edgeBase({ ORAZAKA_EDGE_URL: "http://localhost:8080" }).href, "http://localhost:8080");
});

test("an edge that is not https (or local http) is refused", () => {
  for (const url of ["http://dev.sovkora.com/edge", "file:///etc/passwd", "ftp://x", "not a url", "https://user:pw@dev.sovkora.com"]) {
    assert.throws(() => edgeBase({ ORAZAKA_EDGE_URL: url }), /edge/, url);
  }
});

test("a path is joined under the edge's own path", () => {
  assert.equal(edgeUrl(base, "/api/v1/studios/runs/42"), "https://dev.sovkora.com/edge/api/v1/studios/runs/42");
  assert.equal(edgeUrl(base, "/api/v1/media/x?download=1"), "https://dev.sovkora.com/edge/api/v1/media/x?download=1");
});

test("a path that would leave the edge is refused", () => {
  for (const path of ["//evil.example/x", "/../admin", "/api/%2e%2e/%2e%2e/admin", "@evil.example/x", "api/v1", "/api/v1/../../x", "/a\\..\\b"]) {
    assert.throws(() => edgeUrl(base, path), /edge/, path);
  }
});

test("an id from an answer is one path segment, never a path", () => {
  assert.equal(idSegment("9f1c-42"), "9f1c-42");
  assert.equal(idSegment(42), "42");
  for (const id of ["../x", "a/b", "", ".", "..", "a?b", "a#b", undefined, null, {}]) {
    assert.throws(() => idSegment(id), /id/, String(id));
  }
});

test("a message from an answer is logged on one line, without control characters", () => {
  assert.equal(oneLine("a\nb\r\u001b[31mc\td"), "a b  [31mc d");
});
