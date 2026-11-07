# Authentication Reference

> Complete guide to Orazaka's authentication architecture (local credentials, OAuth2, password recovery).

---

## 1. Overview

| Strategy              | Module               | Configuration                    | Description                             |
| :-------------------- | :------------------- | :------------------------------- | :-------------------------------------- |
| **Local Credentials** | `krizaka-users-core` | `orazaka.identity.auth.local`    | Email + password hashed with BCrypt     |
| **OAuth2 Exchange**   | `krizaka-users-core` | `orazaka.identity.auth.oauth2.*` | NextAuth verifies -> Gateway reconciles |
| **Password Reset**    | `krizaka-users-core` | —                                | Token-based reset with zero-enumeration |

> [!IMPORTANT]
> The backend **never** performs OAuth2 redirects or protocol negotiations. This is handled by NextAuth (BFF). The backend only validates tokens.

---

## 2. Local Credential Flow

```sequence
participant browser: Browser (NextAuth)
participant bff: @orazaka-web-client
participant edge: @orazaka-edge
participant users: @krizaka-users-service
participant core: @krizaka-users-core
participant persistence: @krizaka-users-persistence
participant db: PostgreSQL · krizaka_users_db

browser -> bff: POST /api/auth/login {email, password}
bff -> edge: POST /api/v1/auth/login
edge -> users: route /api/v1/auth
users -> core: IdentityService.authenticate(email, password)
core -> persistence: find the user by e-mail
persistence -> db: select the user
core -> core: BCrypt.matches — the hash never leaves the core
core --> users: User domain record
users --> bff: session JWT
bff --> browser: session cookie
```

- Endpoint: `POST /api/v1/auth/login`
- Payload: `{"email": "...", "password": "..."}`

---

## 3. OAuth2 Token-Exchange Flow

```sequence
participant browser: Browser (NextAuth)
participant provider: Google / GitHub
participant bff: @orazaka-web-client
participant edge: @orazaka-edge
participant users: @krizaka-users-service
participant core: @krizaka-users-core
participant persistence: @krizaka-users-persistence

browser -> provider: OAuth2 authorization (redirects handled by NextAuth)
provider --> browser: ID token
browser -> bff: POST /api/auth/callback {provider, idToken}
bff -> edge: POST /api/v1/auth/oauth
edge -> users: route /api/v1/auth
users -> core: IdentityReconciliationService.reconcile(provider, idToken)
core -> core: OAuth2ProviderVerifier — verify the token, extract the profile
core -> persistence: find the identity by provider and provider id
if the user exists
core -> core: reuse the account
else a new user
core -> persistence: save the new user and its identity
end
core --> users: User domain record
users --> bff: session JWT
bff --> browser: session cookie
```

- Endpoint: `POST /api/v1/auth/oauth`
- Payload: `{"provider": "google", "idToken": "..."}`

---

## 4. Password Recovery Flow

```sequence
participant client: Browser / CLI
participant bff: @orazaka-web-client
participant edge: @orazaka-edge
participant users: @krizaka-users-service
participant core: @krizaka-users-core
participant persistence: @krizaka-users-persistence

## Request a reset
client -> bff: POST /api/auth/forgot {email}
bff -> edge: POST /api/v1/auth/forgot
edge -> users: route /api/v1/auth
users -> core: PasswordRecoveryService.requestPasswordReset(email)
core -> persistence: find the enabled user by e-mail
if the user exists
core -> core: generate a 48-byte token, keep only its SHA-256 hash
core -> persistence: save the reset token (hash, 15 min)
end
users --> client: 200 OK — always, so an address cannot be probed
## Reset the password
client -> bff: POST /api/auth/reset {token, newPassword}
bff -> edge: POST /api/v1/auth/reset
edge -> users: route /api/v1/auth
users -> core: PasswordRecoveryService.resetPassword(token, newPassword)
core -> persistence: find the token by its SHA-256 hash
if the token is valid and active
core -> core: BCrypt.encode, outside the transaction
core -> persistence: update the password hash, delete the token
users --> client: 200 OK
else the token is invalid or expired
users --> client: 400 Bad Request
end
```

- **Forgot Password**: `POST /api/v1/auth/forgot`. Enforces **Zero-Enumeration Invariant**: returns `200 OK` regardless of whether the email exists.
- **Reset Password**: `POST /api/v1/auth/reset` with payload `{"token": "...", "newPassword": "..."}`. Returns `400` on invalid/expired token.

---

## 5. Security & Invariants

- **Token Cryptography**: 48-byte `SecureRandom` token saved as SHA-256 hash (never plaintext). Expire in 15 minutes. Single-use.
- **Password Hashing**: BCrypt is always executed outside `@Transactional` blocks to prevent pool starvation.
- **Provider Extensibility**: Implement `OAuth2ProviderVerifier` and annotate with `@ConditionalOnProperty` to support new sign-in options.
- **Database Schemas**: Password reset tokens and user password-change tracking are defined in `infra/local-db/01-schema.sql`. Flyway is disabled (`spring.flyway.enabled=false`); schemas load via Docker init scripts.
- **Concurrency**: Global security context propagation utilizes a `DelegatingSecurityContextExecutorService` wrapper bean.

---

## Related Documentation

- [Quickstart](101.md)
- [Architecture Reference](_generated/ARCHITECTURE.md)
- [API Reference](_generated/API_REFERENCE.md)
- [Concepts](CONCEPTS.md)
