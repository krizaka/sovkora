import { androidFingerprints, assetLinks } from "@/core/config/app-links";

export const dynamic = "force-dynamic";

/** Android App Links: /.well-known/assetlinks.json. The signing fingerprints are a deployment setting (APP_ANDROID_CERT_SHA256). */
export function GET() {
  const body = assetLinks(androidFingerprints(process.env.APP_ANDROID_CERT_SHA256));
  if (!body) return new Response("Not found", { status: 404 });
  return Response.json(body, { headers: { "cache-control": "public, max-age=300" } });
}
