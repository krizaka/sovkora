import React from "react";
import { ImageIcon, SoundIcon, StudioIcon, VideoIcon } from "@krizaka/icons/native";

/** The @krizaka/icons glyph of a Studio, from what it makes (a Studio key says so: `image-generation`, `speech-synthesis`). */
export function StudioGlyph({
  studioKey,
  color,
  size = 22,
}: Readonly<{ studioKey: string; color: string; size?: number }>): React.JSX.Element {
  const key = studioKey.toLowerCase();
  if (key.includes("video")) return <VideoIcon size={size} color={color} />;
  if (key.includes("image") || key.includes("art-")) return <ImageIcon size={size} color={color} />;
  if (key.includes("speech") || key.includes("audio") || key.includes("sound"))
    return <SoundIcon size={size} color={color} />;
  return <StudioIcon size={size} color={color} />;
}
