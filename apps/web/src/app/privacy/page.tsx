"use client";

import { LegalPage } from "@/features/landing/components/LegalPage";
import { PRIVACY_CONTENT } from "@/features/landing/utils/legal-content";

export default function PrivacyPage() {
  return <LegalPage content={PRIVACY_CONTENT} />;
}
