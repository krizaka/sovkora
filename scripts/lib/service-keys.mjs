/**
 * The service accounts of the products (`svc_<product>`, tier `service`) and their API keys: what may never go silently
 * (krizaka-internal#3214). Pure: no network, no secret, no clock.
 *
 * Verikora dev's key stopped working on 2026-11-08 (first red 22:16Z) after `dev-reset orazaka` cleared `krizaka_users_db`
 * three times that evening: the reset drops every account and key, and its seed puts back the personas, never a product's
 * service account (krizaka-internal#2416). Nothing refused it and nothing named the product.
 */

/** The users database a reset clears, and the one that holds the service accounts and their keys. */
export const USERS_DATABASE = "krizaka_users_db";

/** Read-only: the service accounts that exist now (the underscore is escaped: it is a LIKE wildcard). */
export function serviceAccountsSql() {
  return "SELECT username FROM users WHERE username LIKE 'svc\\_%' ESCAPE '\\' ORDER BY username";
}

/** `svc_verikora` → `verikora` (the account names of `serviceAccount()` turn `-` into `_`). */
export function productsOfAccounts(output) {
  return String(output ?? "")
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l.startsWith("svc_"))
    .map((l) => l.slice("svc_".length));
}

/**
 * Why a reset of `db` is refused, or null. Clearing the users database removes every product's service account and key;
 * it is refused unless the operator names each product it will break (`--drop-service-accounts verikora,…`).
 */
export function serviceAccountRefusal(db, products, acknowledged) {
  if (db !== USERS_DATABASE || products.length === 0) return null;
  const missing = products.filter((p) => !acknowledged.includes(p));
  if (missing.length === 0) return null;
  return `clearing ${USERS_DATABASE} would delete the service account and API key of ${missing.join(", ")} (their calls to Sovkora answer 401 until a new key is made and set in the product). Name them with --drop-service-accounts ${missing.join(",")} once you will rotate them (node scripts/dev-deploy.mjs service-account <product>).`;
}

/** The alert line of a reset that drops accounts: names every product. */
export function droppedAccountsAlert(products) {
  return `ALERT: service accounts dropped by this reset, rotate their keys: ${products.join(", ")}`;
}

/** The previous keys a rotation revokes: same name, never the one just made. */
export function keysToRevoke(keys, name, newKey) {
  return keys.filter((k) => k.name === name && k.id !== newKey?.id);
}

/** The key check of one product: the cheap authenticated call every product makes first. */
export const CHECK_PATH = "/api/v1/studios/installations";

/** The environment variable that holds `product`'s key in the check (`verikora` → `SERVICE_KEY_VERIKORA`). */
export function keyVariable(product) {
  return `SERVICE_KEY_${product.toUpperCase().replace(/-/g, "_")}`;
}

/**
 * Checks each product's key against the edge. `fetchImpl(url, init)` answers a `{ status }`.
 * @returns {Promise<{ product: string, ok: boolean, message: string }[]>}
 */
export async function checkKeys({ products, keyOf, edgeUrl, fetchImpl = fetch }) {
  const out = [];
  for (const product of products) {
    const key = keyOf(product);
    if (!key) {
      out.push({ product, ok: true, skipped: true, message: `${product}: no key configured (${keyVariable(product)}), not checked` });
      continue;
    }
    try {
      const res = await fetchImpl(`${edgeUrl}${CHECK_PATH}`, { headers: { authorization: `Bearer ${key}` }, signal: AbortSignal.timeout(15_000) });
      if (res.status === 200) out.push({ product, ok: true, message: `${product}: the service key is accepted` });
      else if (res.status === 401) out.push({ product, ok: false, message: `${product}: the service key is REJECTED (HTTP 401): the key was revoked or its account dropped, rotate it with node scripts/dev-deploy.mjs service-account ${product}` });
      else out.push({ product, ok: false, message: `${product}: the key check answered HTTP ${res.status} (expected 200)` });
    } catch (e) {
      out.push({ product, ok: false, message: `${product}: the key check could not reach the edge (${e.name})` });
    }
  }
  return out;
}
