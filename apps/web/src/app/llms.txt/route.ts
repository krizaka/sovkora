import { LLMS_HEADERS, llmsText } from "@/app/_seo/seo";

export const dynamic = "force-static";

export function GET() {
  return new Response(llmsText(), { headers: LLMS_HEADERS });
}
