/**
 * @file product-status.ts
 * @description The one place this site reads Sovkora's status from (krizaka/krizaka-com#468). Sovkora is in
 * development like the other Krizaka products: payment is still being tested, accounts are not open, nothing is
 * for sale. While the status is `development`, no public price is shown and no copy may promise availability
 * (`tests/product-status.test.ts` refuses those words). Moving to another status is a decision of the org list,
 * never a copy edit.
 */

export type ProductStatus = "development" | "devPreview" | "available";

export const PRODUCT_STATUS: ProductStatus = "development";

/** True while the product cannot be bought or signed up for: public prices stay « soon », checkout stays closed. */
export const inDevelopment = (status: ProductStatus = PRODUCT_STATUS): boolean => status === "development";
