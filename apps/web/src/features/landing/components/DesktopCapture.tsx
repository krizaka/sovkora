import { mediaUrl } from "@/core/media/cdn";

/** Intrinsic size of the 2x capture of the control panel (2240×1522): the browser reserves it, so nothing shifts. */
const WIDTH = 2240;
const HEIGHT = 1522;
const SMALL = 1120;

/**
 * A screenshot of the real Sovkora Desktop control panel (taken from the 0.1.0 .dmg build), 1x and 2x WebP on the CDN
 * (`sites/orazaka/home/desktop-panel-*.webp`). `eager` for a capture above the fold.
 */
export function DesktopCapture({
  alt,
  className,
  eager = false,
  sizes = "100vw",
}: Readonly<{ alt: string; className?: string; eager?: boolean; sizes?: string }>) {
  const big = mediaUrl(`sites/orazaka/home/desktop-panel-${WIDTH}.webp`);
  const small = mediaUrl(`sites/orazaka/home/desktop-panel-${SMALL}.webp`);
  return (
    // A plain <img> in a <picture>: next/image cannot give a srcset to a file that is already optimised on the CDN.
    <picture>
      <img
        src={big}
        srcSet={`${small} ${SMALL}w, ${big} ${WIDTH}w`}
        sizes={sizes}
        alt={alt}
        width={WIDTH}
        height={HEIGHT}
        loading={eager ? "eager" : "lazy"}
        decoding={eager ? "sync" : "async"}
        className={className}
      />
    </picture>
  );
}
