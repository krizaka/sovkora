import { z } from "zod";

/**
 * What the conversation service's operator surface returns, parsed on arrival: a console that misreads a shape shows a
 * wrong health or a wrong switch. The wire shapes are the records of `StatusController`, `ModelController` and
 * `FeatureController`.
 */
export const HealthSchema = z.object({
  status: z.string(),
  pgVectorConnected: z.boolean(),
  pgVectorExtensionInstalled: z.boolean(),
  ollamaConnected: z.boolean(),
  ollamaModels: z.array(z.string()).default([]),
  defaultProvider: z.string(),
  message: z.string().nullish(),
});
export type Health = z.infer<typeof HealthSchema>;

export const InfrastructureSchema = z.object({ video: z.boolean(), image: z.boolean() });
export type Infrastructure = z.infer<typeof InfrastructureSchema>;

export const ModelEntrySchema = z.object({
  id: z.number().nullish(),
  modelName: z.string(),
  modelLabel: z.string().nullish(),
  category: z.string(),
  providerName: z.string().nullish(),
  isDefault: z.boolean().nullish(),
  compatible: z.boolean().default(true),
  incompatibleReason: z.string().nullish(),
  description: z.string().nullish(),
});
export type ModelEntry = z.infer<typeof ModelEntrySchema>;

export const CapabilitySchema = z.object({
  featureKey: z.string(),
  handlerKey: z.string(),
  routingKey: z.string(),
  billableUnit: z.string().nullish(),
  billableCapability: z.string().nullish(),
  latencyClass: z.string().nullish(),
  enabled: z.boolean(),
});
export type Capability = z.infer<typeof CapabilitySchema>;

/** The identity service's administration (`/api/v1/admin/users`): an account as an administrator sees it — never a credential. */
export const AdminUserSchema = z.object({
  id: z.string(),
  username: z.string(),
  email: z.string(),
  enabled: z.boolean(),
  admin: z.boolean(),
  provider: z.string().nullish(),
  authorities: z.array(z.string()).default([]),
  createdAt: z.string().nullish(),
});
export type AdminUser = z.infer<typeof AdminUserSchema>;

export const AdminUserPageSchema = z.object({
  content: z.array(AdminUserSchema).default([]),
  total: z.number().default(0),
  page: z.number().default(0),
  size: z.number().default(25),
});
export type AdminUserPage = z.infer<typeof AdminUserPageSchema>;

export const AdminAuditEntrySchema = z.object({
  id: z.number(),
  at: z.string().nullish(),
  actor: z.string().nullish(),
  action: z.string(),
  targetId: z.string().nullish(),
  detail: z.string().nullish(),
  requestId: z.string().nullish(),
  outcome: z.enum(["ok", "refused", "failed"]).catch("ok").default("ok"),
});
export type AdminAuditEntry = z.infer<typeof AdminAuditEntrySchema>;
