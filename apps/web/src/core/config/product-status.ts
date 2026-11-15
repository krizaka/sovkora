/**
 * @file product-status.ts
 * @description The one place this site reads Sovkora's status from (krizaka/krizaka-com#468). Sovkora is in
 * development like the other Krizaka products: payment is still being tested, accounts are not open, nothing is
 * for sale. While the status is `development`, no public price is shown and no copy may promise availability
 * (`tests/product-status.test.ts` refuses those words). Moving to another status is a decision of the org list
 * (@krizaka/org), never a copy edit.
 */

import { org, type ProductStatus } from "@krizaka/org";

export type { ProductStatus };

/** Sovkora's status as the org data gives it (`org.products`, @krizaka/org >= 1.5.0, krizaka-internal#3908); a product the
 *  org does not carry is an error, never a default. */
function statusFromOrg(id: string): ProductStatus {
  const found = org.products.find((p) => p.id === id);
  if (!found) {
    throw new Error(`@krizaka/org carries no product "${id}": its status cannot be read`);
  }
  return found.status;
}

export const PRODUCT_STATUS: ProductStatus = statusFromOrg("sovkora");

/** True while the product cannot be bought or signed up for: public prices stay « soon », checkout stays closed. */
export const inDevelopment = (status: ProductStatus = PRODUCT_STATUS): boolean => status === "development";
