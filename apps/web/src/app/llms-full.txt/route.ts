import { LLMS_HEADERS, llmsFullText } from "@/app/_seo/seo";

export const dynamic = "force-static";

export function GET() {
  return new Response(llmsFullText(), { headers: LLMS_HEADERS });
}
