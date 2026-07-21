# Security & Compliance

## Scope

Authentication, authorization, session handling, PHI access logging, audit, and transport security.

## Authentication & session

- Form login at `/login`; session-fixation protection (old session invalidated on successful login).
- Account locks after 5 consecutive failed attempts; Admin can unlock via Admin → User Management; auto-unlocks after 30 minutes.
- Password expiry (90 days); forced-reset flow redirects to `/password/change` before any other page.
- Session timeout: 30 minutes idle (`web.xml`). `SessionCleanupListener` closes the `user_sessions` row on session end.
- Session cookie: `HttpOnly` always; `SameSite=Strict` always; `Secure` flag activated by `ProdSecureCookieListener` under the `prod` profile only (keeps plain-HTTP dev sessions working).

## Authorization (RBAC)

`RoleFilter` enforces path-level access before any controller runs:

| Path prefix | Allowed roles |
| ----------- | ------------- |
| `/admin/**` | ADMIN only |
| `/finance/**` | FINANCE, ADMIN |
| `/review/**` | REVIEWER, ADMIN |
| `/analyst/**` | ANALYST, ADMIN |
| Everything else | All authenticated roles (ANALYST read-only on domain data) |

In-method checks enforce finer-grained rules where path-level is too coarse (e.g. approve/deny restricted to REVIEWER/ADMIN inside `ClaimController`).

## HIPAA minimum-necessary access

Non-reviewer/admin users (STAFF, FINANCE, ANALYST) see only claims they submitted or are assigned to:

- Worklist (`/claims`): scoped by `ClaimService.searchWorklist(scopeToUserId=...)`.
- Detail view (`/claims/{id}`): `ClaimService.canViewClaim()` returns 403 redirect for unauthorized access.
- REVIEWER and ADMIN see all claims.

Every claim view and lifecycle action writes a `phi_access_log` row (member id, claim id, action, user, timestamp).

## CSRF protection

`CsrfFilter` generates a per-session token and exposes it as `${_csrf}` request attribute. Every POST form includes a hidden `<input name="_csrf">`. A POST with a missing or mismatched token returns 403. Static assets and `/health` are exempt (no session created for monitoring polls).

## Security response headers (all profiles)

`SecurityHeadersFilter` runs first in the filter chain and sets the following headers on every response, including error responses:

| Header | Value |
| ------ | ----- |
| `X-Frame-Options` | `DENY` — prevents the app from being embedded in a frame or iframe |
| `X-Content-Type-Options` | `nosniff` — prevents MIME-type sniffing |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; object-src 'none'` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

These headers are active in both `dev` and `prod` profiles because they do not depend on HTTPS and do not impede local development.

## Transport security (prod profile only)

- `HttpsEnforcementFilter` 301-redirects HTTP → HTTPS and emits `Strict-Transport-Security: max-age=31536000; includeSubDomains` on all responses.
- Both activate only when `claims.profile=prod`; dev runs over plain HTTP on `localhost:8090` unchanged.
- Requires a TLS certificate and an HTTPS connector (or reverse proxy) on the prod host.

## Input validation

`ValidationUtil` enforces at every form boundary:

- Required fields, max-length limits, and safe-text check (rejects `<` `>`).
- ICD-10 whitelist regex for diagnosis codes; CPT whitelist for procedure codes; 10-digit NPI check.
- Applied in `ClaimController`, `MemberController`, `ProviderController`.

## Output encoding

- All JSP output via `<c:out>` throughout (enforced since Phase 1).
- Server-generated HTML (EOB, remittance advice) escapes dynamic data at generation via `HtmlUtil.escape`.

## SQL injection prevention

All queries use parameterized `PreparedStatement` / `JdbcTemplate` bound params. No string-concatenated SQL anywhere in the codebase (audited in Phase 8).

## Credentials

DB credentials come from environment variables (`MERIDIAN_DB_USER`, `MERIDIAN_DB_PASSWORD`) in production — never literal values in a packaged file. `StartupValidator` aborts boot with a clear message if they are absent under the `prod` profile. See [configuration.md](configuration.md).

## Batch intake PHI handling

The `InboundClaimFilePollerJob` runs off-session (no HTTP request, no Spring `RequestContextHolder`). Audit entries written during batch processing have `user_id = NULL` (the null-safe design of `AuditService` and `PhiAccessLogService` is intentional and tested). Claims are attributed to the seeded `system` user in `claims.created_by_user_id`.

PHI in intake error messages (member numbers, dates of birth) is masked via `LogMaskUtil.maskDobsInMessage` before being written to `audit_log` or the application log. Raw values are never logged.

## Audit channels

Three distinct, intentional channels — not to be consolidated:

| Table | What it records |
| ----- | --------------- |
| `audit_log` | System-wide: member/provider/plan create, deactivate, lookup refresh |
| `claim_audit` | Claim lifecycle: every status transition with actor and timestamp |
| `phi_access_log` | Every PHI access event: claim view, approve, deny, info request |

Admin audit-log viewer at `/admin/audit` (ADMIN only) provides a paginated, filterable view of `audit_log`.

## Error pages

400, 403, 404, 500 error pages are mapped in `web.xml`. None expose stack traces to the browser. Traces remain in `catalina.out` and the rolling file appender for the dev/ops team.

## Related

[configuration.md](configuration.md) · [operations.md](operations.md) · [user-guide.md](user-guide.md)
