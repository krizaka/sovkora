import { ImageResponse } from "next/og";
import { SITE_NAME, SITE_DESCRIPTION } from "@/app/_seo/seo";

export const alt = "Sovkora — open-source sovereign AI platform, self-hosted";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/** The social preview of every page: the name on Sovkora's orange, one line. */
export default function OpengraphImage() {
  return new ImageResponse(
    <div tw="flex flex-col justify-center w-full h-full p-20 text-white bg-[#0c0a09]">
      <div tw="flex text-8xl font-black">
        {SITE_NAME.toUpperCase()}
        <span tw="text-[#fb923c]">.</span>
      </div>
      <div tw="mt-6 text-4xl font-semibold leading-tight">{SITE_DESCRIPTION}</div>
      <div tw="mt-auto text-2xl text-[#a1a1aa]">www.orazaka.com · A Krizaka product</div>
    </div>,
    size,
  );
}
