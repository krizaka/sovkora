"use client";

import { LegalDocument } from "@/features/landing/components/LegalDocument";
import { PRIVACY_CONTENT } from "@/features/landing/utils/legal-content";

export default function PrivacyPage() {
  return <LegalDocument content={PRIVACY_CONTENT} />;
}
