# Configuration

Every configurable setting, where it lives, its default, and how to override per environment.

## Files and override order

Property resolution runs left-to-right; later files override earlier keys:

1. `src/main/resources/db.properties` — JDBC connection + DBCP2 pool defaults (dev literals).
2. `src/main/resources/application.properties` — application, job, and mail defaults.
3. `$CATALINA_BASE/conf/meridian-claims-prod.properties` — **production overrides** (not
   packaged in the WAR). Copy from `docs/meridian-claims-prod.properties.sample` and fill in
   real values before first prod deploy. Absent in dev; silently skipped.

All three are loaded by `applicationContext.xml` via `<context:property-placeholder>` with
`ignore-resource-not-found="true"`.

## Deployment profile

| Key / variable | Values | Default | How to set |
| -------------- | ------ | ------- | ---------- |
| `claims.profile` | `dev` \| `prod` | `dev` | JVM flag `-Dclaims.profile=prod` or env var `CLAIMS_PROFILE=prod` |

Under `prod`, `StartupValidator` asserts that DB env vars are present and runs Flyway
`validate()` instead of `migrate()`. `HttpsEnforcementFilter` and the Secure session-cookie
flag also activate only under `prod`.

## Database (`db.properties` → overridden in prod overlay)

| Key | Dev default | Prod override |
| --- | ----------- | ------------- |
| `jdbc.url` | `jdbc:postgresql://localhost:5432/meridian_claims` | Set in prod overlay |
| `jdbc.username` | `meridian` | `${MERIDIAN_DB_USER}` (env var — never a literal in prod) |
| `jdbc.password` | `meridian` | `${MERIDIAN_DB_PASSWORD}` (env var — never a literal in prod) |
| `jdbc.pool.initialSize` | 5 | 10 |
| `jdbc.pool.maxTotal` | 20 | 50 |
| `jdbc.pool.maxIdle` | 10 | 20 |
| `jdbc.pool.minIdle` | 5 | 10 |
| `jdbc.pool.maxWaitMillis` | 10000 | 15000 |
| `jdbc.pool.validationQuery` | `SELECT 1` | same |
| `jdbc.pool.testOnBorrow` | true | same |
| `jdbc.pool.testWhileIdle` | true | same |
| `jdbc.pool.timeBetweenEvictionRunsMillis` | 60000 | same |
| `jdbc.pool.removeAbandonedOnBorrow` | true | same |
| `jdbc.pool.removeAbandonedTimeout` | 30 (seconds) | 300 |
| `jdbc.pool.logAbandoned` | true | same |

`MERIDIAN_DB_USER` and `MERIDIAN_DB_PASSWORD` must be set as OS environment variables of the
Tomcat process. `StartupValidator` aborts boot with a clear message if either is absent under
the `prod` profile.

## Application (`application.properties` → overridden in prod overlay for mail)

| Key | Default | Notes |
| --- | ------- | ----- |
| `claims.auto.approve.threshold` | `500.00` | BigDecimal; plan-paid ceiling for auto-approve |
| `claims.sla.SUBMITTED.hours` | `24` | SLA breach threshold per status |
| `claims.sla.IN_REVIEW.hours` | `48` | |
| `claims.sla.PENDING_INFO.hours` | `72` | |
| `claims.sla.escalation.cron` | `0 0 * * * ?` | Quartz cron — hourly SLA scan |
| `claims.escalation.supervisor.email` | `admin@meridian.local` | Escalation alert recipient |
| `claims.mail.enabled` | `false` | `true` in prod overlay — enables real SMTP send |
| `claims.mail.from` | `noreply@meridian.local` | Sender address |
| `claims.mail.smtp.host` | `localhost` | Set in prod overlay |
| `claims.mail.smtp.port` | `25` | Set in prod overlay |
| `claims.mail.smtp.username` | _(empty)_ | Set in prod overlay if relay requires auth |
| `claims.mail.smtp.password` | _(empty)_ | Set in prod overlay if relay requires auth |
| `claims.appeal.internal.days` | `30` | Internal appeal deadline (calendar days) |
| `claims.appeal.external.days` | `3` | External appeal acknowledgement deadline |
| `claims.jobs.stale.cron` | `0 0 2 * * ?` | Nightly 2 AM — abandon stale claims |
| `claims.jobs.rollover.cron` | `0 0 3 * * ?` | Nightly 3 AM — benefit-year rollover |
| `claims.jobs.appeal.sla.cron` | `0 30 * * * ?` | Half-past every hour — appeal SLA scan |
| `claims.jobs.slowquery.cron` | `0 0 4 * * ?` | Nightly 4 AM — slow-query report |
| `claims.slowquery.threshold.ms` | `500` | Queries slower than this are reported |
| `claims.dba.email` | `dba@meridian.local` | Slow-query report recipient |
| `claims.jobs.archive.cron` | `0 0 1 * * ?` | Nightly 1 AM — archive old claims |
| `claims.archive.retention.years` | `7` | Claims older than N years are archived |

## Security policy (`application.properties`)

Account-lockout and password rules. Adjust per your organisation's security standard
without a recompile (resolved by `SecurityPolicy`).

| Key | Default | Notes |
| --- | ------- | ----- |
| `claims.security.lockout.max-attempts` | `5` | Consecutive failed logins before the account locks |
| `claims.security.lockout.minutes` | `30` | Lockout duration once the threshold is hit |
| `claims.security.password.expiry-days` | `90` | Max password age before a reset is forced |
| `claims.security.password.min-length` | `8` | Minimum password length (complexity also requires a digit and a special character) |
| `claims.security.password.bcrypt-rounds` | `12` | BCrypt work factor (log2 rounds); higher = slower hashing |

## UI pagination (`application.properties`)

Rows per page (resolved by `PaginationConfig`).

| Key | Default | Notes |
| --- | ------- | ----- |
| `claims.ui.page-size.list` | `20` | List and worklist screens (claims, members, providers, plans, referrals, prior-auth) |
| `claims.ui.page-size.report` | `50` | Report screens and the audit-log viewer |

## Batch claim intake (`application.properties`)

Multi-format inbound file poller. Intake is inert until `claims.intake.enabled=true`
and a valid `claims.intake.path` is configured.

| Key | Default | Notes |
| --- | ------- | ----- |
| `claims.intake.enabled` | `false` | Set `true` to activate the poller |
| `claims.intake.path` | _(empty)_ | Absolute path to the inbound directory |
| `claims.intake.archive.path` | _(empty)_ | Processed files moved here; defaults to `<intake.path>/archive/` |
| `claims.intake.rejected.path` | _(empty)_ | Files that fail at the file level moved here; defaults to `<intake.path>/rejected/` |
| `claims.intake.system-user-id` | `0` | DB id of the `system` user; `0` triggers a lookup by username. Seeded by V12 migration. |
| `claims.intake.edi.extensions` | `edi,x12,837` | Comma-separated file extensions routed to the X12 EDI 837 parser (case-insensitive) |
| `claims.jobs.intake.cron` | `0 0/5 * * * ?` | Quartz cron — every 5 minutes |

**Supported inbound formats:**

- **FHIR R4 Claim JSON** (`.json`) — single `Claim` resource or a `Bundle` of `Claim` resources. Mapped fields: `patient.identifier.value` (member number), `provider.identifier.value` (NPI), `billablePeriod.start` (date of service), `insurance[focal=true]` (coverage order), `diagnosis[].diagnosisCodeableConcept` (ICD-10), `item[].productOrService` + `item[].net` (CPT + billed amount).
- **X12 EDI 837P/837I** (`.edi`, `.x12`, `.837`, or any extension in `claims.intake.edi.extensions`) — professional and institutional claim transactions. Mapped segments: ISA13 (external reference), NM1\*IL (member), NM1\*82/85 (provider NPI), DTP\*472 (date of service), HI (ICD-10 diagnoses), SV1 (professional line item), SV2 (institutional line item), SBR (coverage order). Files may contain multiple ST/SE transactions; each is an independent claim record.

Both formats converge on the same `ClaimService.submit` pipeline after parsing. Files with unrecognised extensions are skipped with a warning.

## Remittance EDI output (`application.properties`)

| Key | Default | Notes |
| --- | ------- | ----- |
| `claims.remittance.edi.output.path` | _(empty)_ | If set, generated 835 remittance files are also written to this directory as `remittance-{id}.835`. Empty = download-only (no file written to disk). |
| `claims.remittance.edi.production` | `false` | X12 `ISA15` usage indicator. `false` → `T` (test); `true` → `P` (production). **Set to `true` in the prod overlay** so live clearinghouses receive production-flagged 835 files. Leave `false` in dev/staging. |

## Credentials — never in source

DB credentials (`jdbc.username`, `jdbc.password`) come from environment variables in prod.
SMTP credentials (`claims.mail.smtp.username`, `claims.mail.smtp.password`) should also be
set via the prod overlay (which itself lives outside the WAR) rather than committed to source.
No secret should ever appear in a file under `src/`.

## Related

[operations.md](operations.md) · [scheduled-jobs.md](scheduled-jobs.md) · [security.md](security.md) · [../docs/meridian-claims-prod.properties.sample](meridian-claims-prod.properties.sample)
