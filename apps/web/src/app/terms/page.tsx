"use client";

import { LegalDocument } from "@/features/landing/components/LegalDocument";
import { TERMS_CONTENT } from "@/features/landing/utils/legal-content";

export default function TermsPage() {
  return <LegalDocument content={TERMS_CONTENT} />;
}
