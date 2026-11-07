#!/usr/bin/env node
// Fails when the app has no icon, when the icon file is missing, or when it is not a 1024x1024 PNG without alpha
// (App Store Connect shows a blank white square for a build whose icon is missing or has transparency; Apple wants no
// rounded corners either — the system rounds them). The Android adaptive icon needs a 1024x1024 foreground and a background.
// Dependency-free: reads the PNG header. Source of truth: app.json (app.config.js only extends it).
import { readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export function pngInfo(file) {
  const b = readFileSync(file);
  if (b.length < 33 || b.readUInt32BE(0) !== 0x89504e47) return null;
  const colorType = b[25];
  let transparency = false;
  for (let o = 8; o + 8 <= b.length;) {
    const len = b.readUInt32BE(o);
    if (b.toString("latin1", o + 4, o + 8) === "tRNS") transparency = true;
    o += 12 + len;
  }
  return {
    width: b.readUInt32BE(16),
    height: b.readUInt32BE(20),
    alpha: colorType === 4 || colorType === 6 || transparency,
  };
}

export function checkIcons(root) {
  const errors = [];
  const expo = JSON.parse(readFileSync(path.join(root, "app.json"), "utf8")).expo ?? {};
  const resolve = (p) => path.join(root, p);
  const need = (label, p, { alphaOk = false } = {}) => {
    if (!p) return errors.push(`${label}: not set in app.json`);
    if (!existsSync(resolve(p))) return errors.push(`${label}: ${p} does not exist`);
    const info = pngInfo(resolve(p));
    if (!info) return errors.push(`${label}: ${p} is not a PNG`);
    if (info.width !== 1024 || info.height !== 1024)
      errors.push(`${label}: ${p} is ${info.width}x${info.height}, expected 1024x1024`);
    if (!alphaOk && info.alpha) errors.push(`${label}: ${p} has an alpha channel (the iOS icon must be opaque)`);
  };
  need("iOS icon (expo.icon)", expo.ios?.icon ?? expo.icon);
  const adaptive = expo.android?.adaptiveIcon;
  if (!adaptive) errors.push("Android adaptiveIcon: not set in app.json");
  else {
    need("Android adaptive foreground", adaptive.foregroundImage, { alphaOk: true });
    if (adaptive.backgroundImage) need("Android adaptive background", adaptive.backgroundImage);
    else if (!adaptive.backgroundColor)
      errors.push("Android adaptive background: set backgroundImage or backgroundColor");
  }
  return errors;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const errors = checkIcons(path.resolve(path.dirname(fileURLToPath(import.meta.url)), ".."));
  if (errors.length) {
    console.error(errors.map((e) => `check-icons: ${e}`).join("\n"));
    process.exit(1);
  }
  console.log("check-icons: ok");
}
