/**
 * The GPU output store (krizaka-internal#2121): a rented pod writes its outputs (clips, end.json, boot log tail) straight to a
 * storage zone of its own, never to krizaka-assets and never with a site credential. This module is the pure part: the pod's
 * environment, the key layout, the retention, the guard. The store itself is the port of @krizaka/storage (scripts/gpu-out.mjs).
 *
 *   key  = <YYYY-MM-DD>/<run>/<pod>/<name>      (the date folder is what the retention reads: the port lists keys, not dates)
 */

/** The only kind a pod can write to with a plain header today; any other kind is refused, never silently replaced. */
export const POD_WRITABLE_KINDS = { bunny: { headerName: "AccessKey" } };

// @fixed: a pod's outputs are read within a day or two of the run (promotion, the end reason); a week keeps a failed run inspectable and the zone small.
export const DEFAULT_RETENTION_DAYS = 7;

const SAFE = /^[A-Za-z0-9._-]{1,80}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;

export function readSettings(json) {
  const s = typeof json === "string" ? JSON.parse(json) : json;
  for (const f of ["provider", "credentialRef"]) if (!s?.[f]) throw new Error(`gpu-out settings: ${f} is missing`);
  if (!s.coordinates?.endpoint || !s.coordinates?.zone) throw new Error("gpu-out settings: coordinates.endpoint and coordinates.zone are needed");
  const retentionDays = s.retentionDays ?? DEFAULT_RETENTION_DAYS;
  if (!Number.isInteger(retentionDays) || retentionDays < 1) throw new Error("gpu-out settings: retentionDays must be a whole number of days, 1 or more");
  return { ...s, retentionDays };
}

/** `<day>/<run>`: the pod's id is not known before it exists, so the pod adds its own `/<pod>/` segment (RUNPOD_POD_ID) under this prefix. */
export function outPrefix({ day, run }) {
  if (!DAY.test(day)) throw new Error(`gpu-out: day must be YYYY-MM-DD, got ${day}`);
  if (!SAFE.test(run ?? "")) throw new Error("gpu-out: run is not a safe path segment");
  return `${day}/${run}`;
}

/** The environment a pod gets for its outputs: the zone URL under the run's prefix, the header name, the zone's key. Nothing else. */
export function podOutputEnv(settings, secretValue, ids) {
  const kind = POD_WRITABLE_KINDS[settings.provider];
  if (!kind) throw new Error(`gpu-out: a pod cannot write to a "${settings.provider}" store yet (only ${Object.keys(POD_WRITABLE_KINDS).join(", ")}): nothing was started`);
  if (!secretValue) throw new Error(`gpu-out: the credential ${settings.credentialRef} is empty`);
  const { endpoint, zone } = settings.coordinates;
  return {
    GPU_OUT_URL: `https://${endpoint}/${zone}/${outPrefix(ids)}`,
    GPU_OUT_HEADER: kind.headerName,
    GPU_OUT_KEY: secretValue,
  };
}

const POD_ENV_ALLOWED = new Set([
  "WORKER_REF", "PROFILE", "RELAY_URL", "RELAY_AUTH", "RABBIT_USER", "RABBIT_PASSWORD", "POD_TOKEN", "BROKER_CA_B64", "MAX_HOURS", "IDLE_MINUTES",
  "MEASURE_ARGS", "MEASURE_PIP", "STILLS", "HF_TOKEN", "PUBLIC_KEY", "ORAZAKA_BOOT_B64", "GPU_OUT_URL", "GPU_OUT_HEADER", "GPU_OUT_KEY",
]);

/** A pod's env must hold only known names, and no value that is a site credential (`siteSecrets`: the values to keep out, never printed). */
export function podEnvProblems(env, siteSecrets = []) {
  const problems = [];
  for (const [k, v] of Object.entries(env)) {
    if (!POD_ENV_ALLOWED.has(k)) problems.push(`${k}: not an allowed pod variable`);
    else if (/^MEDIA_(STORAGE|ACCOUNT)/.test(k)) problems.push(`${k}: a site credential`);
    if (v && siteSecrets.includes(v)) problems.push(`${k}: holds a site credential`);
  }
  return problems;
}

/** The date folders older than the retention: `keys` are full keys; a day is expired when it is `days` or more before `today` (YYYY-MM-DD). */
export function expiredDays(keys, today, days) {
  const limit = Date.parse(`${today}T00:00:00Z`) - days * 86_400_000;
  const out = new Set();
  for (const key of keys) {
    const day = key.split("/")[0];
    if (DAY.test(day) && Date.parse(`${day}T00:00:00Z`) <= limit) out.add(day);
  }
  return [...out].sort((a, b) => a.localeCompare(b));
}

/** A clip is promoted only when it is a non-empty mp4 or webm by its first bytes (never by its name). */
export function clipKind(bytes) {
  if (!bytes || bytes.length < 12) return null;
  if (String.fromCodePoint(...bytes.slice(4, 8)) === "ftyp") return "mp4";
  if (bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf && bytes[3] === 0xa3) return "webm";
  return null;
}
