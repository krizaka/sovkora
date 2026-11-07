# Content policy: you decide

Orazaka is open source and does not censor. It ships a policy engine that **the operator configures**. A fresh install is permissive: nothing is refused because of what a prompt is about. You decide what your installation refuses, and you can protect your provider accounts from terms you accepted.

## What happens on every Studio run

Before any credit is held, run written or provider reached, Orazaka:

1. Classifies the prompt locally. It never leaves the service. Labels: `SEXUAL_EXPLICIT`, `SEXUAL_SUGGESTIVE`, `MINORS_RISK`, `VIOLENCE_GRAPHIC`, `REAL_PERSON_LIKENESS`, `NONE`.
2. Applies your configuration.
3. Writes the decision to an append-only audit table: labels, decision (`ALLOWED`, `WARNED`, `REFUSED`) and rule. Never the prompt.

A refusal answers `422` with the stable code `content.policy_refused`. Clients translate it.

## The one rule you cannot change

`MINORS_RISK` is always refused, for every tenant and every provider. Sexual content involving a minor is illegal everywhere. No setting turns it off.

## What you configure

| Table                     | What it says                                                                 | Default             |
| ------------------------- | ---------------------------------------------------------------------------- | ------------------- |
| `content_policy_tenant`   | For a subject (`'*'` is the whole install) and a label: `ALLOW` or `REFUSE`. | Everything allowed  |
| `content_provider_policy` | What a provider's terms allow, and `enforcement`: `BLOCK` or `WARN`.         | No restriction      |
| `content_policy_rule`     | Extra classifier patterns.                                                   | Built-in rules only |

Protect a hosted provider account by marking the capability that reaches it:

```sql
INSERT INTO content_provider_policy (provider_key, allows_adult_content, enforcement, note)
VALUES ('orazaka.core.media.video', FALSE, 'BLOCK', 'Hosted provider terms');
```

The same prompt sent to a capability on your own hardware still runs. Refuse something for everyone, then allow it for one actor:

```sql
INSERT INTO content_policy_tenant (subject, label, action) VALUES ('*', 'SEXUAL_EXPLICIT', 'REFUSE');
INSERT INTO content_policy_tenant (subject, label, action) VALUES ('actor-id', 'SEXUAL_EXPLICIT', 'ALLOW');
```

## Test without generating

`POST /api/v1/studios/{studioKey}/policy-check` takes the body of a run and answers labels, decision and rule. It holds no credits and calls no provider.

## Limits today

- Only the input is classified, not the generated frames.
- Direct image jobs outside a Studio are not covered yet.
- Provider terms are marked per capability, not per model.

Our hosted dev instance marks its video capability `BLOCK` for adult content, to respect its provider's terms. That is configuration (`infra/deploy/dev-content-policy.sql`), not code.

## Next

- [Run a Studio](STUDIO_GUIDE.md): where the policy applies.
- [Administration](_generated/ADMIN.md): what an administrator controls.
