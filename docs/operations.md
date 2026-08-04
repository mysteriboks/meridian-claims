# Operations

## Scope

How to deploy, monitor, back up, and recover the application in production, and how to operate its scheduled jobs.

## Deployment

- **Step-by-step production checklist:** [getting-started.md → Production Deployment](getting-started.md#production-deployment) — pre-deploy, deploy, smoke test, rollback, first-deploy operational setup.
- **Local dev procedure:** see [getting-started.md](getting-started.md) — build, provision DB, deploy to local Tomcat, smoke test.
- **Prod property template:** [meridian-claims-prod.properties.sample](meridian-claims-prod.properties.sample) — copy to `$CATALINA_BASE/conf/meridian-claims-prod.properties` on prod hosts.
- WAR builds with `mvn clean package`. Flyway runs automatically on startup under `dev` profile; under `prod` it validates only (apply migrations manually first).

## Health & monitoring

- **`/health`** — DB ping + DBCP2 pool stats (active / idle / max). Returns 200 when healthy. No session created (safe for frequent polling).
- **Logs** — `$CATALINA_BASE/logs/meridian-claims.log`, daily rolling, 30-day retention. Request log (method / path / user / response time) via `LoggingFilter`; requests ≥ 500ms logged at WARN.
- **ERROR alerts** — `SMTPAppender` in prod sends email on every ERROR-level log event. Configure SMTP in the prod overlay; recipients set via `claims.mail.*` properties.
- **Slow-query report** — nightly job (`SlowQueryReportJob`, 4 AM) emails `claims.dba.email` with queries above `claims.slowquery.threshold.ms` (default 500ms).
- **Admin → Operations** (`/admin/operations`) — recent scheduled-job run history and manual job trigger (ADMIN only).
- **Admin → Intake Batches** (`/admin/intake-batches`) — ledger of all processed batch claim files: file name, COMPLETED/PARTIAL/FAILED status, succeeded/quarantined counts, and quarantine error notes (ADMIN only).
- **Admin → Integrations** (`/admin/integrations`) — every inbound X12 837 interchange and its outbound acknowledgments (999/277CA/TA1), with trading-partner attribution (ADMIN only).
- **Admin → Trading Partners** (`/admin/trading-partners`) — the trading-partner master; add/edit/deactivate partners and their transport config (ADMIN only).
- **Admin → Audit Log** (`/admin/audit`) — paginated, filterable system audit log (ADMIN only).

## Database

- **Migrations** — Flyway, append-only. Never edit a migration that has run; add a new one. Under prod, `StartupValidator` calls `flyway.validate()` and aborts boot if the schema is ahead of the code.
- **Backups** — use `pg_dump meridian_claims` or your platform's managed snapshot. Restore with `pg_restore` into a clean database, then deploy the matching WAR (Flyway validates on boot).
- **Connection pool** — DBCP2 tuned via `db.properties` (dev) / prod overlay (prod). Monitor active connections via `/health`. Pool exhaustion surfaces as a 500 error page (not a cryptic trace).
- **Concurrency** — deductible/OOP accumulator uses `SELECT … FOR UPDATE` to serialize concurrent claim submissions for the same member. Verify with a two-session PostgreSQL test before go-live.

### Database migration policy

- Migration scripts live in `src/main/resources/db/migration/` (`V{n}__description.sql`).
- **Never edit a migration that has already run** in any environment.
- To fix a schema issue in prod: write a new migration, deploy, let Flyway apply it.
- `flyway repair` requires explicit DBA approval and must never run automatically.

### Scheduled jobs
- Schedules, manual trigger, and history → see [scheduled-jobs.md](scheduled-jobs.md).
- Data archiving / retention.

### Batch claim intake
- Configure inbound/archive/rejected paths and set `claims.intake.enabled=true` in the prod overlay (see [configuration.md](configuration.md)).
- Drop FHIR R4 JSON (`.json`) or X12 EDI 837 (`.edi`/`.x12`/`.837`) files into the inbound directory; the poller picks them up within the cron interval (default 5 minutes).
- Monitor processing results in **Admin → Intake Batches**.
- To trigger immediately: Admin → Operations → `inboundClaimFilePollerJobDetail` → Run Now.

### Outbound EDI 835 remittance
- From any remittance batch detail screen (`Finance → Remittance`), click **Download 835** to stream the X12 835 file for that batch.
- To also write 835 files to disk automatically, configure `claims.remittance.edi.output.path` in the prod overlay.

### EDI acknowledgments (999 / 277CA / TA1)
- Automatic — every inbound X12 837 file processed by either poller gets a TA1 (if the
  interchange itself was malformed), a 999 (functional acknowledgment), and a 277CA
  (claim-level acknowledgment). No separate job or config needed to activate them.
- To also write acknowledgment files to disk, configure `claims.integration.edi.output.path`
  in the prod overlay; otherwise they're generated and logged to `edi_transactions.detail` only.
- Monitor in **Admin → Integrations**.

### Claim status inquiry (X12 276/277)
- Automatic — drop a `.276` file into the shared inbound directory or a trading partner's
  inbound path and either poller answers it with a 277, logged to `edi_transactions` (and,
  for a trading partner, delivered back through their own outbound transport) exactly like a
  999/277CA. No separate job, config flag, or new screen — reuses everything Phase 12/13 built.
- Read-only: a `.276` never creates, updates, or resubmits a claim.
- Monitor in **Admin → Integrations** (same screen; look for `276`/`277` in the Type column).

### Prior authorization request (X12 278)
- Automatic — drop a `.278` file the same way. **Unlike a 276, a certified request creates a
  real prior authorization** — a normal `ACTIVE` row in `prior_authorizations`, indistinguishable
  from one entered by staff, created via the same `PriorAuthorizationService` code path.
- A request that fails validation (unknown member/provider, invalid procedure code, missing or
  invalid requested certification period) is denied and creates nothing.
- Response reports UM `A1` (certified) or `A3` (not certified) — there is no "pended" outcome,
  since the prior-auth domain itself has no pended status.
- Monitor in **Admin → Integrations** (look for `278` in the Type column); the created
  authorization itself is visible wherever prior authorizations are normally viewed.

### Real-time eligibility check (X12 270/271)
- Not a poller or file-drop feature — a user-initiated action. From any member's detail
  screen, click **Check Eligibility**; `EligibilityCheckService` builds and sends a 270 request,
  parses the 271 response, and records the result immediately (synchronous, no cron interval).
- No live clearinghouse partner exists yet, so this always runs against `MockEligibilityClient`,
  which synthesizes a realistic 271 from the member's own coverage records
  (`member_coverage`/`isActiveOn(now)`) rather than a real network call — see
  [configuration.md](configuration.md) → "Real-time eligibility check (Phase 16)".
- Result (`ACTIVE`/`INACTIVE`/`ERROR`) and the plan/coverage snapshot appear immediately in the
  **Eligibility Check History** panel on the same member screen (last 10 checks).
- Not logged to Admin → Integrations — eligibility checks are member-scoped events in
  `eligibility_checks`, not trading-partner file exchanges.

### Enrollment ingestion (X12 834)
- Automatic — drop a `.834` file into the shared inbound directory or a trading partner's
  inbound path; either poller applies it via `EnrollmentIntakeService`. Checked after the
  `.276`/`.278` checks and before claim-submission dispatch.
- Per INS loop (one member per loop): **Add** (`021`) creates the member and a PRIMARY coverage
  record if a plan resolves; **Termination** (`024`) stamps a termination date on the member's
  matching open-ended coverage record; **Change** (`001`) updates demographics only.
- Bad records (unknown member for a termination/change, unresolvable plan name, missing/invalid
  dates, unrecognized maintenance code) are quarantined individually — the rest of the file
  still processes.
- File-level idempotency via SHA-256 hash in `enrollment_batches` — re-dropping the same file is
  a no-op, same as batch claim intake (Phase 9).
- Monitor in **Admin → Enrollment Batches** (not Admin → Integrations — enrollment has no
  acknowledgment document, so it never touches `edi_transactions`).
- To trigger immediately: Admin → Operations → `inboundClaimFilePollerJobDetail` or
  `tradingPartnerPollerJobDetail` → Run Now (same jobs handle every file type).

### EFT/ACH payment issuance (X12 834 companion — NACHA)
- Automatic — runs when a payment batch is exported (**Finance → Batches → Export CSV**), right
  alongside the existing 835 remittance generation. Not a separate action.
- Providers with ACH banking info configured (routing/account/type, set on the provider detail
  screen) get paid via a NACHA ACH credit entry instead of check; providers without it are
  skipped — the batch stays partially or fully check-paid, never blocked.
- If not a single provider in the batch has banking info configured, no `eft_payments` row is
  created at all — the batch is exactly as it always was before this phase.
- The TRN reassociation number is stamped on both the ACH file and the paired 835's TRN
  segment, so a provider can match the deposit to the remittance advice.
- View EFT status, the TRN, and providers-paid/skipped counts on the Finance batch detail
  screen; **Download ACH File** regenerates the NACHA text on demand; **Mark Settled** is a
  manual confirmation — there is no live bank feed to detect settlement automatically.
- Configure Meridian's own ODFI identity via `claims.eft.origin.*` in the prod overlay before
  going live — see [configuration.md](configuration.md) → "EFT/ACH payment issuance".

### Trading partners (SFTP transport)
- Configure each partner at **Admin → Trading Partners**: X12 identity (ISA/GS ids), transport
  type, and for SFTP — host, port, username, and a `transport_credential_ref` **key name**
  (never the password itself).
- Set the matching password in the prod overlay as `claims.transport.credential.<ref>` before
  activating an SFTP partner — see [security.md](security.md) → "Trading-partner credentials".
- `TradingPartnerPollerJob` polls every **active** partner independently on
  `claims.jobs.trading-partner.cron` (default every 10 minutes); one partner's transport
  failure never blocks the others.
- To trigger immediately: Admin → Operations → `tradingPartnerPollerJobDetail` → Run Now.
- `LOCAL` partners need no credentials — just an `inbound_path`/`outbound_path` pair, exactly
  like the global intake directory, just partner-scoped.

### Incident response

**DB unreachable at startup**
`StartupValidator` aborts the Spring context with `STARTUP ABORTED: database is unreachable`. Tomcat logs the message in `catalina.out`. Check: PostgreSQL running? Correct host/port in `conf/meridian-claims-prod.properties`? `MERIDIAN_DB_USER` / `MERIDIAN_DB_PASSWORD` env vars set? Network/firewall between Tomcat host and DB host?

**Flyway validation failure at startup (prod)**
`STARTUP ABORTED [prod]: Flyway schema validation failed`. The deployed WAR is ahead of or behind the DB schema. Apply or roll back the pending migration manually (`flyway migrate` or restore the DB from backup), then restart.

**Scheduled job failure**
Every job writes a `scheduled_job_log` row with `status = 'FAILED'` and an error message. Check via Admin → Operations → Recent Job Runs. For a persistent failure, check `catalina.out` for the stack trace at the recorded `started_at` time. Jobs are independent — a failure in one does not affect others.

**Mail send failure**
If `claims.mail.enabled=true` and SMTP is misconfigured, alert emails silently fail (the app logs a warning but does not throw). Check `catalina.out` for `LoggingMailService` WARN lines. Verify `claims.mail.smtp.host`, port, and credentials in the prod overlay.

**Connection pool exhausted**
The application returns a 500 error page when no connections are available. Monitor `/health` active/max counts. Under sustained load, increase `jdbc.pool.maxTotal` in the prod overlay and restart. Check for connection leaks via `jdbc.pool.logAbandoned=true` output in the log.

## Reporting verification (against real PostgreSQL)

The reporting queries (dashboard widgets + 12 reports) are covered by `Phase7DaoIT`, which
executes every query against H2 in PostgreSQL-compatibility mode. H2 is **not a perfect
PostgreSQL oracle** — date arithmetic and a few functions differ — so after any change to
`JdbcReportDAO`, also run this manual pass against a real PostgreSQL instance:

1. Provision a dev database and apply migrations (Flyway runs on startup).
2. Load the sample dataset: `psql -U meridian -d meridian_claims -f samples/sample_data.sql`.
3. Deploy and sign in, then open each report and confirm it renders real rows (not an
   empty table or a 500 error):
   - `/dashboard` — stat tiles, claims-by-status, top denial reasons, recent activity
   - `/reports/claims-summary`, `/reports/claims-detail`, `/reports/denials`,
     `/reports/payments`, `/reports/member-activity?memberId=1`,
     `/reports/provider-activity?providerId=1`, `/reports/sla-performance`,
     `/reports/adjudication-rules`, `/reports/appeals`, `/reports/cob`,
     `/reports/subrogation`, `/reports/fee-schedule-coverage`
4. On each report, click **Export CSV** and confirm the file opens in Excel with correct
   headers and quoting.

Note: report queries surface SQL errors (the DAO rethrows `DAOException` → 500), so a broken
report fails loudly rather than showing an empty table. Dashboard widgets are deliberately
resilient (a failing widget returns empty without blanking the page).

## Related

- [configuration.md](configuration.md) · [scheduled-jobs.md](scheduled-jobs.md) · [security.md](security.md)
