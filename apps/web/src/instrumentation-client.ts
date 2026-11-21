/**
 * @file instrumentation-client.ts
 * @description Runs in the browser before the app hydrates (Next.js convention).
 *
 * The pages' Content-Security-Policy allows no `eval` (src/core/config/security-headers.ts). zod compiles its object
 * parsers with `new Function` when it may, and probes for it once (a refused probe is a CSP violation report, then a
 * fallback): `jitless` makes it use its interpreter from the start. The setting is global (`globalThis`), so it holds
 * for every copy of zod in the bundle.
 */
import { config } from "zod";

config({ jitless: true });
