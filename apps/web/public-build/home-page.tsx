import { ProductHome } from "@/features/landing/components/ProductHome";

/**
 * `/fr` and `/en` of the public export: the product page (the template of @krizaka/ui) with Sovkora's own words, colour and media.
 * Static: the dev app's home reads the session cookie and carries the figures and the film, which an export has no place for.
 */
export default function HomePage() {
  return <ProductHome />;
}
