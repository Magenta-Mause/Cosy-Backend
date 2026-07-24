# Authentication & Authorization

Cosy uses **stateless, self-issued JWT** on a Spring Security backend, consumed by the
React frontend. The backend is its own identity provider — it mints and validates its
own tokens (no external IdP). See also the Security section of
[SPRING_BOOT.md](SPRING_BOOT.md).

## Backend model (Spring Security + jjwt)

- **Stateless.** `SessionCreationPolicy.STATELESS`, CSRF disabled (documented reason:
  stateless token API). No server-side session.
- **A `JwtFilter extends OncePerRequestFilter`**, registered
  `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`. It:
  1. resolves `Authorization: Bearer <token>`,
  2. validates the signature and that it is an **identity** token,
  3. loads the `UserEntity` by the token subject (user UUID),
  4. sets a custom `AuthenticationToken(subject, user)` on the `SecurityContextHolder`.
- **Route rules in one `SecurityFilterChain` bean.** Public prefixes (auth endpoints,
  actuator, api-docs, webhooks) are `permitAll()`; everything else `.authenticated()`.
- **Signing:** HMAC (symmetric) via the `jjwt` library. The secret comes from
  `@ConfigurationProperties(prefix = "jwt")` — injected in production via env
  (`JWT_SECRET_KEY`), never a committed default.

### Token types

Tokens carry a `tokenType` claim; the filter only accepts the identity token for
authentication. Expirations are separate config values (`jwt.*-expiration-time`).

| Token | Purpose | Contents |
|-------|---------|----------|
| **Identity (access)** | short-lived, authenticates each request | rich user claims: `userId`, `username`, role, capabilities, … |
| **Refresh** | long-lived, mints new identity tokens | minimal (`userId`, `username`) |

The **identity token is deliberately claim-rich** so the frontend can read auth state
without an extra round-trip.

### Delivery: header vs cookie (`TokenMode`)

- The identity token is sent by the SPA as `Authorization: Bearer …`.
- The refresh token is issued as an **httpOnly cookie** (`auth.cookie.secure` toggled
  per environment). A `TokenMode` of `COOKIE` (browser) vs `DIRECT` (token in body)
  lets non-browser clients opt out of cookies.

## Authorization

- Coarse-grained: route rules in the filter chain + `@PreAuthorize` on service methods.
- For fine-grained access that depends on the specific resource (not just a role),
  use dedicated policy/authorization components rather than inline checks.

## Frontend side (React)

- The Orval `customInstance` (axios) attaches the bearer token and handles a
  **silent refresh on 401** using the refresh cookie.
- Route access control lives in TanStack Router `beforeLoad` guards, **never** inside
  page components.
- An auth-context/hook decodes the identity token claims for UI state.

## DO / DON'T

**DO:**
- Keep the API stateless; validate the JWT in a dedicated filter, not in business logic.
- Split access vs refresh tokens; keep the refresh token in an httpOnly cookie.
- Source the signing secret from env/`@ConfigurationProperties`; fail closed.
- Put auth endpoints behind rate limiting (+ captcha on public sign-up/login if exposed).

**DON'T:**
- Hardcode the JWT secret, roles, or issuers in source (a committed default is a smell,
  even when overridden in prod).
- Do access control in controllers/components — use the filter chain, `@PreAuthorize`,
  and router `beforeLoad`.
- Store the access token where XSS can read it if it can be avoided; prefer the
  in-memory + httpOnly-refresh pattern.
