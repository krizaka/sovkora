import { appleAppSiteAssociation } from "@/core/config/app-links";

export const dynamic = "force-dynamic";

/**
 * Apple's universal links file: served at /.well-known/apple-app-site-association, no extension, no redirect,
 * `application/json`. The Team ID is a deployment setting (APP_IOS_TEAM_ID); without it there is nothing to serve.
 */
export function GET() {
  const body = appleAppSiteAssociation(process.env.APP_IOS_TEAM_ID);
  if (!body) return new Response("Not found", { status: 404 });
  return Response.json(body, { headers: { "cache-control": "public, max-age=300" } });
}
