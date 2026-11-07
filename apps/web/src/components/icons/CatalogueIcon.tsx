import type { ComponentType } from "react";
import {
  AgentIcon,
  BillingIcon,
  ChatIcon,
  CheckIcon,
  EditIcon,
  EyeIcon,
  GoalIcon,
  HeartIcon,
  ImageIcon,
  type IconProps,
  KnowledgeIcon,
  PackIcon,
  SearchIcon,
  ShieldIcon,
  SoundIcon,
  StudioIcon,
  VideoIcon,
} from "@krizaka/icons";

/**
 * The catalogue's `iconKey`s (Studios, packs, shelves — data written by the pack manifests) drawn with the Krizaka
 * signature icons. A key without a glyph of its own falls back to the Studio icon, never to a blank.
 */
const ICONS: Record<string, ComponentType<IconProps>> = {
  studio: StudioIcon,
  image: ImageIcon,
  video: VideoIcon,
  vision: EyeIcon,
  speech: SoundIcon,
  audio: SoundIcon,
  search: SearchIcon,
  send: ChatIcon,
  "shield-check": ShieldIcon,
  notebook: EditIcon,
  "list-checks": CheckIcon,
  "chart-line": KnowledgeIcon,
  sparkles: AgentIcon,
  heart: HeartIcon,
  target: GoalIcon,
  briefcase: BillingIcon,
  repeat: PackIcon,
  pack: PackIcon,
};

export interface CatalogueIconProps extends IconProps {
  /** The `iconKey` of a Studio, a pack or a shelf. */
  iconKey: string | null | undefined;
}

/** A catalogue `iconKey` as a signature icon (its node lit in the accent). */
export function CatalogueIcon({ iconKey, ...props }: Readonly<CatalogueIconProps>) {
  const Glyph = (iconKey && ICONS[iconKey]) || StudioIcon;
  return <Glyph nodeColor="var(--kz-accent)" {...props} />;
}
