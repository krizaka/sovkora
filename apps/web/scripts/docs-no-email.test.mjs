// A code block of the docs must not hold an e-mail-looking address. Cloudflare's e-mail obfuscation rewrites it in the
// HTML it serves ("[email protected]"), so the text the browser hydrates differs from the text React rendered and
// every visitor of the page gets React error #418 (found by the UI crawl, orazaka-web-client#124). Write `<email>`.
import { readdirSync, readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const dir = new URL("../content/docs/", import.meta.url);
const EMAIL = /[A-Za-z0-9_.+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}/;

test("no fenced code block of content/docs holds an e-mail address", () => {
  const found = [];
  for (const name of readdirSync(dir).filter((f) => f.endsWith(".md"))) {
    let fence = false;
    readFileSync(new URL(name, dir), "utf8")
      .split("\n")
      .forEach((line, i) => {
        if (/^ {0,3}(```|~~~)/.test(line)) fence = !fence;
        else if (fence && EMAIL.test(line)) found.push(`${name}:${i + 1}`);
      });
  }
  assert.deepEqual(found, [], "write <email> in a code block, Cloudflare rewrites addresses in the served HTML");
});
