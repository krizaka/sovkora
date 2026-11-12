#!/usr/bin/env node
/**
 * Service key watch (krizaka-internal#3214): each product's Sovkora key answers 200 on the edge, or the run fails with a
 * `::error` naming the product (.github/workflows/dev-service-keys.yml, every 30 minutes).
 *
 *   node scripts/dev-service-keys.mjs [product…]      keys from SERVICE_KEY_<PRODUCT> (never printed)
 */
import { pathToFileURL } from "node:url";
import { checkKeys, keyVariable } from "./lib/service-keys.mjs";

/** The products whose service account is watched. A product is added here when it gets a service account. */
export const PRODUCTS = Object.freeze(["verikora"]);

export async function main(argv = process.argv.slice(2), environ = process.env, fetchImpl = fetch, log = console) {
  const products = argv.length ? argv : PRODUCTS;
  const edgeUrl = environ.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
  const results = await checkKeys({ products, keyOf: (p) => environ[keyVariable(p)], edgeUrl, fetchImpl });
  for (const r of results) {
    if (r.ok) log.log(r.message);
    else log.log(`::error title=Service key of ${r.product}::${r.message}`);
  }
  return results.every((r) => r.ok) ? 0 : 1;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) process.exitCode = await main();
