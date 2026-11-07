/* global Buffer */
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, mkdirSync } from "node:fs";
import { deflateSync } from "node:zlib";
import os from "node:os";
import path from "node:path";
import { checkIcons } from "./check-icons.mjs";

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc = (buf) => {
  let c = 0xffffffff;
  for (const x of buf) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const t = Buffer.from(type);
  const l = Buffer.alloc(4);
  l.writeUInt32BE(data.length);
  const c = Buffer.alloc(4);
  c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
};
function png(w, h, colorType) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = colorType;
  const bpp = colorType === 6 ? 4 : 3;
  const raw = Buffer.alloc((w * bpp + 1) * h);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
function app(files, expo) {
  const dir = mkdtempSync(path.join(os.tmpdir(), "icons-"));
  mkdirSync(path.join(dir, "assets"));
  for (const [name, buf] of Object.entries(files)) writeFileSync(path.join(dir, "assets", name), buf);
  writeFileSync(path.join(dir, "app.json"), JSON.stringify({ expo }));
  return dir;
}
const good = {
  icon: "./assets/icon.png",
  android: { adaptiveIcon: { foregroundImage: "./assets/fg.png", backgroundColor: "#0d0d10" } },
};
const files = { "icon.png": png(1024, 1024, 2), "fg.png": png(1024, 1024, 6) };

test("a complete icon set passes", () => assert.deepEqual(checkIcons(app(files, good)), []));
test("no icon in app.json fails", () =>
  assert.match(checkIcons(app(files, { android: good.android }))[0], /iOS icon.*not set/));
test("a missing icon file fails", () =>
  assert.match(checkIcons(app({ "fg.png": files["fg.png"] }, good))[0], /does not exist/));
test("an icon that is not 1024x1024 fails", () =>
  assert.match(checkIcons(app({ ...files, "icon.png": png(512, 512, 2) }, good))[0], /512x512/));
test("an icon with alpha fails", () =>
  assert.match(checkIcons(app({ ...files, "icon.png": png(1024, 1024, 6) }, good))[0], /alpha/));
test("no adaptive icon fails; a transparent foreground is fine", () => {
  assert.match(checkIcons(app(files, { icon: good.icon }))[0], /adaptiveIcon/);
  assert.deepEqual(checkIcons(app(files, good)), []);
});
