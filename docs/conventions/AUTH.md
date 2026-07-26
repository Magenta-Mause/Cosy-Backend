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
  1. resolves the token from `Authorization: Bearer <token>` — and **only** from that
     header; there is no request-parameter fallback (see the WebSocket note below),
  2. validates the signature, the `cosy-backend` issuer, a non-empty subject, and that
     the `tokenType` claim is the **identity** token,
  3. loads the `UserEntity` by the token subject (user UUID),
  4. sets a custom `AuthenticationToken(subject, user)` (a
     `UsernamePasswordAuthenticationToken` granting `ROLE_<role>`) on the
     `SecurityContextHolder`.
- **Route rules in one `SecurityFilterChain` bean** (`SecurityConfiguration`). The chain
  ends in `.requestMatchers("/**").authenticated()`, so **everything is protected by
  default and every `permitAll()` is a deliberate hole.** The exempt set covers auth
  endpoints, actuator, springdoc, a handful of public GETs, the internal metric-push
  endpoints and the WebSocket handshake — read `SecurityConfiguration` for the current
  list rather than trusting one written down here.
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

## The client-facing contract

What this repo guarantees to a client (Cosy-Frontend or any other consumer) — the
endpoints live on `AuthorizationApi` under `/auth`:

| Endpoint | Purpose |
|---|---|
| `POST /auth/login` | credentials in, identity token in `LoginResponseDto`; refresh token per `TokenMode` |
| `GET /auth/token` | exchange the refresh token (cookie or body) for a fresh identity token |
| `POST /auth/logout` | clears the refresh cookie (`maxAge=0`) |

Clients send the identity token as `Authorization: Bearer <token>` on every request —
**every** HTTP request, with no exceptions. The zip-download endpoints stream their
response but are ordinary header-authenticated requests; they are not a special case.

The **WebSocket handshake** is the sole exception, because a browser cannot set headers
on it: `JwtHandshakeInterceptor` accepts the token as an `authToken` **query parameter**
on `/v1/ws`. That endpoint is `permitAll()` in the filter chain, so the interceptor — not
`JwtFilter` — is what authenticates it. Treat that URL as sensitive: query strings are
logged by every reverse proxy Cosy ships with (nginx, Caddy, ingress-nginx), so a
handshake URL in an access log is a usable identity token until it expires.

Because the identity token carries the role and limit claims, a client can render auth
state without an extra round-trip — but **claims are a snapshot from mint time**. A role
or quota change does not take effect until the token is refreshed, so never treat a
client-side claim check as enforcement; the server-side policy is the authority.

Frontend-side implementation details (how Cosy-Frontend stores the token and guards
routes) live in the Cosy-Frontend repo — don't mirror them here, they drift.

## DO / DON'T

**DO:**
- Keep the API stateless; validate the JWT in `JwtFilter`, not in business logic.
- Enforce resource-level access with `@NeedsValidation` + a policy, so the rule sits next
  to the other policies rather than inline in a controller body.
- Source the signing secret from `JwtProperties`; override `COSY_JWT_SECRET_KEY` in any
  real deployment.
- Add a new `Operation` + policy when you add an endpoint that touches a user-owned
  resource — an endpoint with no `@NeedsValidation` is authenticated but otherwise
  unrestricted.

**DON'T:**
- Hardcode roles or issuers in source, or rely on the committed dev secret anywhere real.
- Add a `permitAll()` matcher without being sure the handler is safe unauthenticated —
  the chain ends in `.requestMatchers("/**").authenticated()`, so new endpoints are
  protected by default and every `permitAll()` is a deliberate hole.
- Widen the `JwtFilter` to accept the refresh token for authentication — the token-type
  check is the only thing keeping a long-lived token out of ordinary requests.
