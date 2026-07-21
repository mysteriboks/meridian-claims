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
