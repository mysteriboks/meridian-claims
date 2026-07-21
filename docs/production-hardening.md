# Meridian Claims — Production Hardening Playbook

A standalone reference for hardening the application before a production deployment.
It captures the security, validation, observability, performance, resilience, and
configuration work required to run Meridian Claims safely in production, and the items
to **re-apply whenever a new ingress surface is added** (e.g. inbound file/EDI feeds or
a provider portal).

---

## Non-negotiable invariant — local runnability is preserved

**The app must remain runnable locally over plain HTTP (`http://localhost:8090`) at all
times.** Every hardening item that would otherwise break local development is gated
behind the **`prod` profile**; the default **`dev`** profile keeps plain HTTP, visible
stack traces in logs, bundled dev credentials, and no outbound mail. Nothing in this
playbook may make the default local run fail.

| Concern | `dev` (default, local) | `prod` |
| ------- | ---------------------- | ------ |
| Transport | plain HTTP on :8090, no redirect | HTTPS enforced, HTTP→HTTPS redirect, HSTS |
| Session cookie | Secure flag **off** (request-driven), SameSite=Strict, HttpOnly | Secure **on** + HSTS |
| DB credentials | bundled `db.properties` literals | env vars only, no literal fallback (fail-fast if absent) |
| Error pages | generic page in browser, **stack trace still in dev log** | generic page; no trace anywhere user-visible |
| Mail / ERROR alerts | disabled (`claims.mail.enabled=false`) | SMTPAppender + alerts on |
| CSRF | **on** (does not impede local HTTP) | on |
| Input validation | on | on |
| PHI log masking | on | on |

CSRF, input validation, PHI masking, and "no stack traces in the browser" apply in
**both** profiles — they don't impede local HTTP and are always-on safety.

---

## The dev/prod profile mechanism (foundation)

- **Profile source:** a single `claims.profile` property, values `dev` | `prod`,
  **default `dev`**. Resolved from JVM system property / env var
  (`-Dclaims.profile=prod`) with fallback to `application.properties`.
- **Helper:** an `AppProfile` bean exposing `isProd()` / `isDev()` for Java branching.
- **Property overlay:** dev defaults in `application.properties`; prod overrides in an
  **external** `file:${catalina.base}/conf/meridian-claims-prod.properties` appended to the
  `<context:property-placeholder>` location list (`ignore-resource-not-found=true`). The file
  is **not** packaged in the WAR — it exists only on prod hosts, so prod credentials never sit
  on the classpath and a dev box (where the file is absent) boots from the literal
  `db.properties` values with no setup. Template: `docs/meridian-claims-prod.properties.sample`.
- **Startup banner:** `StartupValidator` (an `InitializingBean`) logs the active
  profile prominently at boot and, under `prod`, asserts prod prerequisites
  (DB env vars present, etc.) — fail-fast with a clear message.

---

## Hardening checklist

### Security

- [x] **Security response headers (both profiles).** `SecurityHeadersFilter` is registered
  first in the filter chain and sets `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; object-src 'none'`,
  and `Referrer-Policy: strict-origin-when-cross-origin` on every response (including
  error responses). Active in both dev and prod — these headers do not require HTTPS.
- [ ] **HTTPS enforcement (prod only).** Do **not** put an unconditional
  `<security-constraint>`/`CONFIDENTIAL` in `web.xml` — that force-redirects localhost to
  a non-existent HTTPS connector (connection refused). Implement an
  `HttpsEnforcementFilter` (outermost) that is **inert unless `prod`**: in prod it
  301-redirects `http`→`https` and emits `Strict-Transport-Security`; in dev it passes
  through. Document container/proxy TLS termination as the alternative.
- [ ] **Secure session cookie (prod only).** Leave `web.xml` `<secure>` commented (keeps
  the request-driven default). Set Secure programmatically/in prod context only.
  SameSite=Strict + HttpOnly already in place (`META-INF/context.xml` + `web.xml`).
- [ ] **CSRF on all state-changing forms (both profiles).** `CsrfFilter` + per-session
  token exposed to JSPs; validate on every POST; 403 on mismatch. Add a hidden
  `${_csrf}` field to **every POST form**: claims submit/approve/deny/request-info/
  assign/notes/resubmit/readjudicate; appeals submit/approve/deny/withdraw; finance
  payments/batches/subrogation; admin operations (bulk re-adjudicate, trigger-job);
  user admin; lookups; login; password change. Verify end-to-end locally — must be
  applied consistently or it silently blocks flows.
- [x] **XSS prevention.** `<c:out>` on all user-supplied output — enforced repo-wide.
  Server-generated HTML (EOB/remittance) escapes dynamic data at generation (`HtmlUtil`).
  XSS vulnerabilities in report JSPs were audited and fixed. `SecurityHeadersFilter`
  additionally enforces a CSP that prevents inline script execution.
- [ ] **SQL injection audit.** All queries use `PreparedStatement` / bound params —
  audit-and-confirm (no string-concatenated values, including the report DAO).
- [ ] **Sensitive-data masking in logs (both profiles).** Mask DOB and member numbers to
  last-4 via a `LogMaskUtil`; audit `LOG.*` call sites that include PHI.
- [ ] **Global error pages.** Add `400.jsp`, `404.jsp`, `500.jsp` (keep `403.jsp`); map all
  in `web.xml` (`<error-code>` + `<exception-type>` for uncaught). No stack traces to the
  browser in either profile; traces still logged to console+file in dev. Confirm report
  `DAOException` rethrows surface as the 500 page, not a blank screen. Optional
  correlation id.

### Input validation hardening (both profiles)

- [ ] Server-side validation on every form: required fields, length limits.
- [ ] Whitelist regex for codes: ICD-10, CPT/procedure, NPI (NPI = 10 digits).
- [ ] Centralize in `ValidationUtil`; reject with field-level errors via the existing
  flash/error pattern (not exceptions). Unit-test boundary + reject cases.

### Logging & observability

- [ ] **`LoggingFilter`:** method, path, resolved user, response time per request.
- [ ] Log4j: keep the existing `DailyRollingFileAppender` (daily rotation already configured);
  add explicit retention; add a **prod-only `SMTPAppender`** for `ERROR` events (prod log4j
  overlay) — dev stays console+file (consistent with `claims.mail.enabled=false`).
- [ ] Slow-query logging at 500ms (request-level via `LoggingFilter`; a scheduled
  slow-query report job covers the batch view).
- [ ] **Admin audit-log viewer:** read-only `/admin/audit` over `audit_log`, searchable by
  user/date/action (reuse `AuditService`/`AuditLogDAO` + `Page<T>`).

### Performance

- [ ] DBCP2 pool min/max/timeout tuned via profile (prod values in the external
  `conf/meridian-claims-prod.properties`; dev keeps the current small pool).
- [ ] Pagination enforced on every list query — audit for unbounded `SELECT`; the known
  `findAll`/dropdown lookups are bounded master-data (document the exception).
- [ ] **Load test (manual/operational):** JMeter, 500 concurrent users, 10k claims;
  dashboard must stay < 3s. Documented in `docs/operations.md`, not an automated CI test.

### Resilience

- [ ] **Connection-pool exhaustion → graceful error page** (the 500/`DataAccessResourceFailureException`
  mapping), not a cryptic trace.
- [ ] **Graceful shutdown:** drain in-flight requests / close pool cleanly. Datasource has
  `destroy-method="close"`; Quartz `waitForJobsToCompleteOnShutdown` is set.
- [ ] **Flyway never auto-repairs in prod:** `StartupValidator` migrate-validate only under prod.

### Configuration management

- [ ] All environment-specific values in `application.properties` / external `conf/meridian-claims-prod.properties`.
- [ ] `dev` and `prod` profiles (see mechanism above).
- [ ] **DB credentials via env vars only in prod** — `${MERIDIAN_DB_USER}` / `${MERIDIAN_DB_PASSWORD}`,
  no literal fallback; `StartupValidator` aborts with a clear message if absent. Dev keeps
  literal `db.properties` values so local boot is unchanged.

### Testing

- [ ] Confirm existing: adjudication-rule table-driven tests; `ClaimService` transitions +
  `OptimisticLockException`; SLA-breach logic; DAO H2 integration.
- [ ] Add tests for hardening units: `CsrfFilter`/token, `ValidationUtil`, `LogMaskUtil`,
  `AppProfile`, `HttpsEnforcementFilter` (dev pass-through vs prod redirect), error-page routing.
- [ ] Measure coverage; close gaps to clear >80% on the service and adjudication layer.

### Deployment

- [ ] Maven WAR packaging.
- [ ] Prod `context.xml`/datasource note.
- [ ] **Deployment checklist** (env vars, `-Dclaims.profile=prod`, TLS, post-deploy smoke test) —
  see `docs/getting-started.md` → Production Deployment.

---

## Production readiness criteria

The deployment is hardened when all of the following hold:

- No stack traces visible to any user.
- All forms are CSRF- and XSS-protected.
- All DAO queries are verified as parameterised.
- Unit-test coverage exceeds 80% on the service and adjudication layer.
- Load test passes at 500 concurrent users.
- App starts within 30 seconds or exits with a clear diagnostic.

---

## Re-apply when adding a new ingress surface

Whenever a new way into the system is added, re-apply the relevant items above against
the new surface:

- **Inbound file/EDI intake:** validate & quarantine bad inbound files **before** the
  adjudication transaction; enforce idempotency on re-submitted files; the file poller
  runs as a background job (no HTTP session — audit is null-safe by design); apply the
  input-validation whitelists to parsed fields; mask PHI in any intake logs.
- **Provider portal / external API:** a new authentication/authorization boundary →
  re-run the full Security + Input-validation + CSRF/transport checklist against the new
  surface; HTTPS is mandatory for any external-facing endpoint.

---

## Verification

Default build → deploy → `http://localhost:8090`: `/health` returns 200, login works
(cookie not dropped), submit a claim, open a report — **all unchanged**. With
`-Dclaims.profile=prod`, HTTPS redirect + Secure/HSTS + env-credential enforcement
activate; with default `dev`, they don't.

## Related

[security.md](security.md) · [operations.md](operations.md) ·
[configuration.md](configuration.md) · [getting-started.md](getting-started.md#production-deployment)
