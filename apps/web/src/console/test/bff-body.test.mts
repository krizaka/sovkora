// The console's BFF relays a multipart upload as it came — bytes and boundary — so the manual import of the law corpus
// (ADR-089 §8, POST /api/v1/admin/law/imports) reaches the service whole; a JSON body stays JSON.
import { test } from "node:test";
import assert from "node:assert/strict";
import { forwardedBody } from "../core/config/bff-body.ts";

const URL_ = "https://admin.orazaka.com/api/v1/admin/law/imports";

test("a multipart upload keeps its bytes and its boundary", async () => {
  const form = new FormData();
  form.append("file", new Blob(["<loi/>"], { type: "application/xml" }), "ccq.xml");
  form.append("datasetUrl", "https://www.donneesquebec.ca/ccq.xml");
  const request = new Request(URL_, { method: "POST", body: form });
  const sentType = request.headers.get("content-type") ?? "";
  const { body, contentType } = await forwardedBody(request, "POST");
  assert.equal(contentType, sentType);
  assert.match(contentType, /^multipart\/form-data; boundary=/);
  const relayed = await new Request(URL_, {
    method: "POST",
    body,
    headers: { "content-type": contentType },
  }).formData();
  assert.equal(relayed.get("datasetUrl"), "https://www.donneesquebec.ca/ccq.xml");
  assert.equal(await (relayed.get("file") as File).text(), "<loi/>");
});

test("a JSON write is relayed as JSON text", async () => {
  const request = new Request(URL_, {
    method: "POST",
    body: JSON.stringify({ reason: "Eric: wrong article" }),
    headers: { "content-type": "application/json" },
  });
  assert.deepEqual(await forwardedBody(request, "POST"), {
    body: '{"reason":"Eric: wrong article"}',
    contentType: "application/json",
  });
});

test("a read carries no body", async () => {
  assert.deepEqual(await forwardedBody(new Request(URL_), "GET"), { body: undefined, contentType: "application/json" });
});
