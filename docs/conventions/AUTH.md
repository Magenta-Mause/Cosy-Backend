# Authentication & Authorization

Cosy uses **stateless, self-issued JWT** on a Spring Security backend, consumed by the
React frontend. The backend is its own identity provider — it mints and validates its
own tokens (no external IdP). See also the Security section of
[SPRING_BOOT.md](SPRING_BOOT.md).

## Backend model (Spring Security + jjwt)

- **Stateless.** `SessionCreationPolicy.STATELESS`, CSRF disabled (documented reason:
  stateless token API). No server-side session.
- **A `JwtFilter extends OncePerRequestFilter`** (`security/jwtfilter`), registered
  `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`. It:
  1. resolves the token from `Authorization: Bearer <token>` (falling back to an
     `authToken` request parameter, used by streaming/WebSocket handshakes),
  2. validates the signature, the `cosy-backend` issuer, a non-empty subject, and that
     the `tokenType` claim is the **identity** token,
  3. loads the `UserEntity` by the token subject (user UUID),
  4. sets a custom `AuthenticationToken(subject, user)` (a
     `UsernamePasswordAuthenticationToken` granting `ROLE_<role>`) on the
     `SecurityContextHolder`.
- **Route rules in one `SecurityFilterChain` bean** (`SecurityConfiguration`). Public
  matchers (`/auth/**`, `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, a few public
  GET endpoints like `/game-server`, `/footer`, public metrics/logs, the internal
  metric-push endpoints, and `/v1/ws/**`) are `permitAll()`; everything else is
  `.authenticated()`.
- **Signing:** HMAC (symmetric) via the `jjwt` library (`Keys.hmacShaKeyFor`). The secret
  comes from `@ConfigurationProperties(prefix = "jwt")` (`JwtProperties`), bound from
  `jwt.secret-key`. In production it must be overridden via env
  (`COSY_JWT_SECRET_KEY`); a dev default **is** committed in `application.yaml` (flagged
  "change in prod") — treat that committed default as dev-only, never a real secret.

### Token types

Tokens carry a `tokenType` claim (`IDENTITY_TOKEN` / `REFRESH_TOKEN`); the filter only
accepts the identity token for authentication. Both are minted by `JwtUtils` with the
`cosy-backend` issuer and the user UUID as the subject. Expirations are separate config
values (`jwt.identity-token-expiration-time` / `jwt.refresh-token-expiration-time`).

| Token | Purpose | Contents |
|-------|---------|----------|
| **Identity (access)** | short-lived, authenticates each request | subject = user UUID, plus `username`, `role`, and capability claims: `cpu_cores_limit`, `memory_limit`, `can_create_game_servers` |
| **Refresh** | long-lived, mints new identity tokens | subject = user UUID, plus `username` and `role` |

The **identity token is deliberately claim-rich** so the frontend can read auth state
(role, resource limits, whether the user may create servers) without an extra round-trip.

### Delivery: header vs cookie (`TokenMode`)

- The identity token is sent by the SPA as `Authorization: Bearer …`.
- The refresh token is delivered per the `TokenMode` request param (`controllers.TokenMode`,
  default `COOKIE`): `COOKIE` (browser) issues it as a `refreshToken` **httpOnly** cookie
  (`sameSite=Strict`, `path` scoped to `…/auth/token`, `maxAge` = refresh expiry);
  `DIRECT` returns it in the response body (`LoginResponseDto`) for non-browser clients.
- **Caveat (current state):** the cookie's `secure` flag is hardcoded `false` in
  `AuthorizationController` — it is **not** environment-toggled. A real deployment behind
  TLS should set `Secure`; treat this as a known gap, not the intended production posture.

## Authorization

- Coarse-grained: route rules in the `SecurityFilterChain`, plus role-based access from
  the `ROLE_<role>` authority set on the `AuthenticationToken`.
- Fine-grained, resource-aware access uses a **custom access-management aspect** (not
  Spring's `@PreAuthorize`): controller methods are annotated with
  `@NeedsValidation(Operation.X)` and the resource argument with `@ResourceId`.
  `AuthorizationAspect` resolves the resource and delegates to the matching policy in
  `security/accessmanagement/policies` (e.g. `GameServerPolicy`, `UserPolicy`).

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
- Scatter access control as inline checks in controllers/components — use the filter
  chain, the `@NeedsValidation`/policy aspect, and router `beforeLoad`.
- Store the access token where XSS can read it if it can be avoided; prefer the
  in-memory + httpOnly-refresh pattern.
