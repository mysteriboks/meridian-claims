# Scheduled Jobs

Self-contained operator reference for every Quartz job running inside the Meridian Claims WAR.
All jobs are in-process (same JVM as the web application), declared in `applicationContext.xml`,
and instantiated via `AutowiringSpringBeanJobFactory` so Spring `@Autowired` and `@Value`
fields are populated at runtime.

---

## Summary Table

| Job class | Default cron | Runs | What it does | Config key |
| --- | --- | --- | --- | --- |
| `SlaEscalationJob` | `0 0 * * * ?` | Top of every hour | Detects SLA breaches on active claims; emails supervisor; records breach row | `claims.sla.escalation.cron` |
| `AppealSlaEscalationJob` | `0 30 * * * ?` | Half-past every hour | Detects appeals past their `deadline_date`; emails supervisor; records breach row | `claims.jobs.appeal.sla.cron` |
| `StaleClaimJob` | `0 0 2 * * ?` | 02:00 nightly | Transitions `PENDING_INFO` claims whose info-request due date has passed to `ABANDONED` | `claims.jobs.stale.cron` |
| `BenefitYearRolloverJob` | `0 0 3 * * ?` | 03:00 nightly | Audit-log check: warns when a plan's deductible accumulator is keyed to a prior benefit year | `claims.jobs.rollover.cron` |
| `SlowQueryReportJob` | `0 0 4 * * ?` | 04:00 nightly | Queries `pg_stat_statements` for slow queries and emails a report to the DBA | `claims.jobs.slowquery.cron` |
| `ClaimArchiveJob` | `0 0 1 * * ?` | 01:00 nightly | Moves terminal-state claims older than the retention threshold from `claims` to `claims_archive` | `claims.jobs.archive.cron` |
| `InboundClaimFilePollerJob` | `0 0/5 * * * ?` | Every 5 minutes | Polls the configured inbound directory for claim files; parses and submits them through the adjudication pipeline; moves files to `archive/` or `rejected/` | `claims.jobs.intake.cron` |

All cron expressions follow Quartz 6-field format: `seconds minutes hours day-of-month month day-of-week`.

---

## Shared Behavior

### Spring autowiring inside Quartz jobs

Quartz creates job instances itself, bypassing the Spring container. The project configures
`AutowiringSpringBeanJobFactory` as the Quartz `JobFactory` so that after Quartz instantiates
a job class, Spring immediately injects `@Autowired` beans and resolves `@Value` properties.
Without this factory, every `@Autowired` field would be `null` at execution time.

### ScheduledJobLog — run history

Every job calls `ScheduledJobLogService` at three points:

1. `jobLogService.start(jobName)` — inserts a `scheduled_job_log` row with `status=RUNNING`
   and records the start timestamp. Returns an integer `logId`.
2. `jobLogService.complete(logId, recordsProcessed)` — updates the row to `status=SUCCESS`,
   sets `completed_at`, and stores the count of records the job acted on.
3. `jobLogService.fail(logId, errorMessage)` — updates the row to `status=FAILED` and stores
   the exception message.

The `scheduled_job_log` table schema:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `SERIAL PRIMARY KEY` | |
| `job_name` | `VARCHAR` | Java class simple name |
| `started_at` | `TIMESTAMP` | Set by `start()` |
| `completed_at` | `TIMESTAMP` | Set by `complete()` or `fail()` |
| `status` | `VARCHAR` | `RUNNING`, `SUCCESS`, or `FAILED` |
| `records_processed` | `INTEGER` | Count of entities acted on; `0` for informational jobs |
| `error_message` | `TEXT` | Populated only on `FAILED` runs |

### Viewing run history

Open **Admin → Operations** (requires `ADMIN` role). The bottom section of the page, "Recent
Scheduled Job Runs", displays the latest rows from `scheduled_job_log` in a table with columns:
Job, Started, Completed, Status, Records, Error. Status badges are color-coded: green for
`SUCCESS`, yellow/orange for `RUNNING`, red/grey for `FAILED`.

### Triggering a job manually

The same Admin → Operations page provides a "Manually Trigger a Scheduled Job" form. Select
the job name from the dropdown and click **Run Now**. A confirmation dialog is shown before
submission. The form posts to `/admin/operations/trigger-job` (CSRF-protected), which calls
`scheduler.triggerJob(...)` on the live Quartz `Scheduler` bean. The job runs asynchronously
in the Quartz thread pool; refresh the page after a few seconds to see the result in the
history table.

Only jobs registered in the `triggerableJobs` list exposed by `AdminOperationsController` appear
in the dropdown. All seven jobs listed in this document are triggerable.

### Graceful shutdown

The Quartz scheduler bean is configured with `waitForJobsToCompleteOnShutdown=true`. When the
application server shuts down and the Spring context is destroyed, the scheduler waits for any
currently executing jobs to finish before allowing the JVM to exit. This prevents a nightly
archive or accumulation job from being interrupted mid-write. Allow up to a few minutes for
shutdown to complete if a job is actively running.

---

## Job Reference

---

### SlaEscalationJob

**Cron config key:** `claims.sla.escalation.cron`
**Default cron:** `0 0 * * * ?` (top of every hour)

#### What it does

Scans claims currently in the statuses `SUBMITTED`, `IN_REVIEW`, and `PENDING_INFO`. For each
claim in those statuses it asks `SlaService.isBreached(claim)` whether the claim has exceeded
its SLA threshold. If breached and no breach row already exists for that claim+status pair, it
records a new breach and alerts the supervisor. The job is idempotent: the
`slaBreachDAO.existsForClaimStatus(claimId, status)` guard ensures a claim is never double-alerted
for the same status breach.

#### SLA thresholds

Thresholds are configured in `application.properties` and read by `SlaService`:

| Status | Config key | Default |
| --- | --- | --- |
| `SUBMITTED` | `claims.sla.SUBMITTED.hours` | 24 hours |
| `IN_REVIEW` | `claims.sla.IN_REVIEW.hours` | 48 hours |
| `PENDING_INFO` | `claims.sla.PENDING_INFO.hours` | 72 hours |

A value of `0` disables SLA enforcement for that status.

#### What it queries

Pages through `claimDAO.search(null, status, page, 100)` for each watched status — 100 claims
per page. Inserting a breach row does not change claim status, so the filtered result set
remains stable across pages; OFFSET-based pagination is safe.

#### Actions taken per breach

1. Calls `slaService.computeExpectedBy(status, claim.getStatusEnteredAt())` to determine the
   deadline timestamp.
2. Inserts a row into `sla_breaches` (`claim_id`, `status`, `expected_by`).
3. Inserts a `claim_audit` row with `event_type=SLA_ESCALATED`, `old_status=status`,
   `new_status=status`, notes `"SLA breached in status <STATUS>"`.
4. Emails the supervisor (configured by `claims.escalation.supervisor.email`) with subject
   `"SLA Breach: Claim <claimNumber>"` and a plain-text body showing claim number, status,
   expected-by timestamp, and detection time.

#### Logging

- `INFO` at start and completion: `"SlaEscalationJob: starting scan"` / `"SlaEscalationJob: done — N new breach(es) recorded"`.
- `ERROR` on unhandled exception, followed by `jobLogService.fail(...)`.

#### Operational notes

- The `records_processed` count in `scheduled_job_log` reflects only new breaches recorded in
  that run, not the total number of claims scanned.
- Claims in `APPROVED`, `DENIED`, `PAID`, etc. are never scanned — they are outside the three
  watched statuses.
- The supervisor email defaults to `admin@meridian.local`. Override with
  `claims.escalation.supervisor.email` in production.
- Email delivery requires `claims.mail.enabled=true` and a reachable SMTP host. If mail is
  disabled the breach row and audit entry are still written; only the email is skipped.

---

### AppealSlaEscalationJob

**Cron config key:** `claims.jobs.appeal.sla.cron`
**Default cron:** `0 30 * * * ?` (half-past every hour)

#### What it does

Scans all `OPEN` appeals whose `deadline_date` has passed and for which no breach row with
`status='APPEAL'` exists for the associated claim. On first breach it records the event and
emails the supervisor. Mirrors `SlaEscalationJob` but operates on the `appeals` table rather
than the `claims` table.

#### Appeal SLA thresholds

The deadlines are set when an appeal is created, driven by appeal type:

| Appeal type | Config key | Default |
| --- | --- | --- |
| Internal | `claims.appeal.internal.days` | 30 days |
| External | `claims.appeal.external.days` | 3 days |

`AppealSlaEscalationJob` does not read these config keys directly — it compares `new Date()`
against `appeal.getDeadlineDate()` which is already stored on the record. The config keys
govern how `deadline_date` is calculated at appeal-creation time.

#### What it queries

Calls `appealDAO.findByStatus("OPEN")` to retrieve all open appeals in a single query (no
pagination). As with `SlaEscalationJob`, inserting a breach row does not change appeal status,
so the result set is stable.

#### Actions taken per breach

1. Inserts a row into `sla_breaches` with `claim_id`, `status='APPEAL'`,
   `expected_by=appeal.getDeadlineDate()`.
2. Inserts a `claim_audit` row with `event_type=APPEAL_SLA_BREACHED` and notes recording the
   appeal id and deadline.
3. Emails the supervisor with subject `"Appeal SLA Breach: appeal <id>"` and a body showing
   appeal id, associated claim id, appeal type, deadline, and detection time.

#### Logging

- `INFO` at start and completion: `"AppealSlaEscalationJob: starting scan"` / `"AppealSlaEscalationJob: done — N appeal breach(es) recorded"`.
- `ERROR` on unhandled exception.

#### Operational notes

- The breach key is `(claim_id, 'APPEAL')`. Each claim can have at most one appeal breach row
  regardless of how many appeals exist for it. If a claim has multiple appeals, the first
  overdue appeal triggers the breach; subsequent appeals on the same claim are not re-alerted.
- Email and supervisor-address behavior are identical to `SlaEscalationJob`.

---

### StaleClaimJob

**Cron config key:** `claims.jobs.stale.cron`
**Default cron:** `0 0 2 * * ?` (02:00 nightly)

#### What it does

Finds `PENDING_INFO` claims whose open info-request due date has passed and transitions them to
`ABANDONED`. The abandonment criterion is `InfoRequest.due_date`, not a fixed calendar age.
A claim is only eligible if it currently has an open (unanswered) info request with a non-null
`due_date` that is before the current timestamp.

#### Two-pass design

The job deliberately separates reading from writing to avoid a pagination corruption bug:

- **Pass 1 (read-only):** Pages through all `PENDING_INFO` claims in batches of 100.
  For each, calls `infoRequestDAO.findOpenByClaimId(claim.getId())` and checks whether the
  open request's `due_date` is past. Collects eligible claims into a local list. Does not
  mutate any rows.
- **Pass 2 (write):** Iterates the collected list and calls `claimDAO.updateStatus(...)` with
  `ClaimStatus.ABANDONED`. Transitioning a claim out of `PENDING_INFO` removes it from the
  status filter, which would corrupt OFFSET pagination if writes and reads were interleaved.

#### Actions taken per claim

1. Calls `claimDAO.updateStatus(claimId, "ABANDONED", claimVersion)` — uses optimistic locking
   via `version`; if the version has changed since Pass 1 the update silently no-ops (the claim
   was touched by another process between passes).
2. Inserts a `claim_audit` row: `event_type=ABANDONED`, `old_status=PENDING_INFO`,
   `new_status=ABANDONED`, notes `"Stale: PENDING_INFO past info_request due_date"`.

No email is sent. Operators can see abandoned claims via the Claims search screen filtered to
`ABANDONED` status.

#### Logging

- `INFO` at start and completion: `"StaleClaimJob: starting scan"` / `"StaleClaimJob: done — N claim(s) abandoned"`.
- `ERROR` on unhandled exception.

#### Operational notes

- `records_processed` in the job log equals the number of claims successfully transitioned to
  `ABANDONED`.
- If a claim's `PENDING_INFO` info request has no `due_date` set (null), the claim is skipped
  and never abandoned by this job. Operators must manually resolve it or set a due date on the
  info request.
- Claims that move from `PENDING_INFO` to another status between Pass 1 and Pass 2 are
  silently skipped due to the optimistic-lock check.

---

### BenefitYearRolloverJob

**Cron config key:** `claims.jobs.rollover.cron`
**Default cron:** `0 0 3 * * ?` (03:00 nightly)

#### What it does

Audit-only check. Verifies that no deductible accumulator row attached to an active plan is
keyed to a prior benefit year. It does not delete, zero, or modify any accumulator data.
The system's design is that each plan year naturally receives a fresh `deductible_accumulators`
row on the first claim of that year (keyed by `benefit_year_start`); prior-year rows are
retained forever for audit. This job detects cases where something unexpected has prevented
that new-row creation.

#### Benefit-year boundary calculation

For each active plan:

1. Reads `plan.benefit_year_start` (a stored month/day template date).
2. Computes the anniversary of that date in the current calendar year.
3. If the anniversary has not yet occurred this year, uses last year's date instead.
4. Compares each accumulator's `benefit_year_start` against this computed boundary. An
   accumulator whose `benefit_year_start` predates the boundary is a "prior-year anomaly."

#### What it queries

- `planDAO.findAllActive()` — all plans with `active=true`.
- `accumulatorDAO.findByPlanId(planId)` — all accumulator rows for the plan.

No writes are performed.

#### Actions taken per anomaly

Logs a `WARN` line per anomaly:

```
BenefitYearRolloverJob: plan id=<id> has prior-year accumulator id=<id> bys=<date> — retained for audit; new claims will use bys=<currentBys>
```

No email is sent. Anomalies are surfaced in the application log and in the `records_processed`
count of the job's `scheduled_job_log` row.

#### Logging

- `INFO` at start and completion.
- `WARN` per prior-year accumulator found.
- `ERROR` on unhandled exception.

#### Operational notes

- A non-zero `records_processed` count does not indicate data corruption; it signals that at
  least one plan has a prior-year accumulator on file. Because the system retains old rows for
  audit, this is expected after the first benefit-year rollover for any plan.
- An anomaly worth investigating is one where `records_processed` increases nightly without
  new claims arriving for a plan in the new benefit year, which would suggest the new-year
  accumulator seed path is broken.

---

### SlowQueryReportJob

**Cron config key:** `claims.jobs.slowquery.cron`
**Default cron:** `0 0 4 * * ?` (04:00 nightly)

#### What it does

Queries PostgreSQL's `pg_stat_statements` view for SQL statements whose mean execution time
exceeds the configured threshold. Emails a plain-text report to the DBA. If
`pg_stat_statements` is unavailable (extension not installed, or running against H2 in tests)
the job logs a notice and exits cleanly — it never marks itself `FAILED` in that case.

#### Prerequisites

The PostgreSQL extension must be enabled:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

And `pg_stat_statements` must be listed in `postgresql.conf`:

```
shared_preload_libraries = 'pg_stat_statements'
```

A database restart is required after adding it to `shared_preload_libraries`.

#### Configuration

| Property | Default | Description |
| --- | --- | --- |
| `claims.slowquery.threshold.ms` | `500` | Mean execution time in milliseconds above which a query is included |
| `claims.dba.email` | `dba@meridian.local` | Recipient address for the report email |

#### What it queries

```sql
SELECT query, calls, round(mean_exec_time::numeric, 2) AS mean_ms
FROM pg_stat_statements
WHERE mean_exec_time > ?
ORDER BY mean_exec_time DESC
LIMIT 20
```

Returns at most 20 rows, ordered by slowest first. `mean_exec_time` is in milliseconds on
PostgreSQL 13 and later.

#### Actions taken

If slow queries are found, sends one email to `claims.dba.email` with subject
`"Meridian Claims — Nightly Slow Query Report"`. The body lists each query with its `mean_ms`
and call count. The `records_processed` count in the job log equals the number of queries
included in the report.

If no queries exceed the threshold, or if `pg_stat_statements` is unavailable, no email is
sent and `records_processed` is recorded as `0`.

#### Logging

- `INFO` when no slow queries are found or when `pg_stat_statements` is unavailable.
- `INFO` at completion with the count of reported queries.
- `ERROR` on any other unhandled exception.

#### Operational notes

- `pg_stat_statements` accumulates data since the last `pg_stat_statements_reset()` call or
  since PostgreSQL started. Mean times can be distorted by one-off expensive runs early in
  uptime. Reset stats after a schema change or major data load if historical noise is a
  problem: `SELECT pg_stat_statements_reset();`
- In lower environments (H2, local dev without the extension) the job silently skips; this is
  intentional and does not indicate a configuration error.
- Email delivery requires `claims.mail.enabled=true`. If mail is disabled the job still
  completes as `SUCCESS` but the report is never delivered.

---

### ClaimArchiveJob

**Cron config key:** `claims.jobs.archive.cron`
**Default cron:** `0 0 1 * * ?` (01:00 nightly)

#### What it does

Moves terminal-state claims that are older than the retention threshold from the live `claims`
table to the `claims_archive` table. Archiving keeps the `claims` table lean and ensures active
worklists never surface years-old closed records. Archived claims remain searchable via Admin →
Operations → "Search Archived Claims".

#### Terminal states eligible for archiving

Only claims in terminal states (those from which no further transitions occur) are archived:
`PAID`, `VOIDED`, `REPLACED`, `DENIED`, and `ABANDONED`. Claims in any in-progress status are
never moved.

#### Configuration

| Property | Default | Description |
| --- | --- | --- |
| `claims.archive.retention.years` | `7` | Minimum age of a terminal claim before it is eligible for archiving |

The cutoff date is computed as `today minus retentionYears years` at the moment the job runs.

#### What it queries and writes

Delegates entirely to `claimArchiveDAO.archiveClaimsOlderThan(cutoffDate)`, which performs an
atomic insert-and-delete (typically `INSERT INTO claims_archive SELECT ... FROM claims WHERE
... AND updated_at < ?` followed by `DELETE FROM claims WHERE ...`). The exact SQL is defined
in the DAO; the job supplies only the cutoff date.

#### Logging

- `INFO` at completion: `"ClaimArchiveJob: done — N claim(s) archived (cutoff=<date>)"`.
- `ERROR` on unhandled exception.
- `records_processed` in the job log equals the number of claims moved.

#### Viewing archived claims

Navigate to **Admin → Operations** and click the "Search Archived Claims" link. This opens the
archive search screen at `/admin/operations/archive`, which queries `claims_archive` rather
than `claims`. Archived claims are read-only; no status transitions or edits are possible on
them.

#### Operational notes

- Increasing `claims.archive.retention.years` in production requires a restart; the new value
  takes effect on the next job run. Decreasing the value will cause more claims to be archived
  on the next run — review the impact before lowering it.
- The archive is permanent in the sense that there is no automated un-archive path. Restoring
  a specific claim to the live table requires a manual SQL operation: copy the row from
  `claims_archive` back to `claims` and delete from `claims_archive`.
- All related child rows (`claim_line_items`, `claim_diagnoses`, `payments`, etc.) should be
  handled by the DAO's archive query with matching `WHERE claim_id IN (...)` clauses. Verify
  foreign key constraints or archiving order if the DAO query is ever modified.
- `records_processed = 0` on a run is normal when no claims have aged past the cutoff. It does
  not indicate a failure.

---

---

### InboundClaimFilePollerJob

**Cron config key:** `claims.jobs.intake.cron`
**Default cron:** `0 0/5 * * * ?` (every 5 minutes)

#### What it does

Polls the configured inbound directory (`claims.intake.path`) for claim files. For each
file it detects the format by extension, parses it via the appropriate parser, and submits
each record through `ClaimService.submit` → the full adjudication pipeline. Successfully
processed files (including partially quarantined ones) are moved to `archive/`; files that
fail entirely at the file level are moved to `rejected/`.

The job is inert when `claims.intake.enabled=false` (the default) or when `claims.intake.path`
is blank. Annotated `@DisallowConcurrentExecution` — only one poller run can be active at a time.

#### Supported formats

| Extension | Parser | Format |
| --- | --- | --- |
| `.json` | `FhirClaimFileParser` | FHIR R4 Claim JSON (single resource or Bundle) |
| `.edi`, `.x12`, `.837` (configurable) | `X12Edi837Parser` | X12 EDI 837P (professional) or 837I (institutional) |

Extensions are case-insensitive. Files with unrecognised extensions are skipped with a warning log.

#### Idempotency

A SHA-256 hash of each file's content is stored in `claim_intake_batches` with `UNIQUE(file_hash)`.
Re-dropping the same file content is a no-op regardless of filename. Stale `PROCESSING` rows (from
a prior run that was interrupted) are automatically reclaimed on the next run.

#### Per-record fault isolation

One malformed or invalid record within an otherwise valid file is quarantined individually and
recorded in the batch ledger. The remaining records in the same file are processed normally.
The quarantine reason is written to `audit_log` and is visible in **Admin → Intake Batches**.

#### Actions taken per file

1. Reads the file content and computes the SHA-256 hash.
2. Checks `claim_intake_batches` for an existing completed row (idempotency skip).
3. Inserts a `PROCESSING` ledger row.
4. Parses the file via the format-appropriate parser.
5. Submits each valid record through `ClaimService.submit(req, systemUserId)`.
6. Updates the ledger row to `COMPLETED` or `FAILED` with succeeded/quarantined counts.
7. Moves the file to `archive/` (any records accepted) or `rejected/` (all records failed).

#### Operational monitoring

- Batch history visible in **Admin → Intake Batches** (ADMIN only).
- Individual run history visible in **Admin → Operations → Recent Scheduled Job Runs**.
- To trigger a manual poll: Admin → Operations → select `inboundClaimFilePollerJobDetail` → Run Now.
- `records_processed` in `scheduled_job_log` equals the number of files handled (not the number of claims).

#### Logging

- `INFO` per file processed with final succeeded/quarantined counts.
- `WARN` per quarantined record (reason included, DOB/member number masked via `LogMaskUtil`).
- `ERROR` on file-move failure or unexpected exception.

#### Operational notes

- The batch system user (`username=system`, `active=false`, role STAFF) is seeded by the V12 migration.
  `claims.intake.system-user-id=0` (the default) triggers a lookup by username at first use.
- PHI in intake logs (member numbers, DOBs) is masked via `LogMaskUtil` before being written to
  `audit_log` or the application log, consistent with HIPAA minimum-necessary.
- If `claims.intake.path` does not exist at startup, the job logs a warning and skips; it does not
  abort the application.

---

## Configuration Quick Reference

All properties belong in `application.properties` (or the environment-specific overlay).

```properties
# Cron schedules (Quartz 6-field format)
claims.sla.escalation.cron=0 0 * * * ?
claims.jobs.appeal.sla.cron=0 30 * * * ?
claims.jobs.stale.cron=0 0 2 * * ?
claims.jobs.rollover.cron=0 0 3 * * ?
claims.jobs.slowquery.cron=0 0 4 * * ?
claims.jobs.archive.cron=0 0 1 * * ?
claims.jobs.intake.cron=0 0/5 * * * ?

# SLA thresholds
claims.sla.SUBMITTED.hours=24
claims.sla.IN_REVIEW.hours=48
claims.sla.PENDING_INFO.hours=72

# Appeal SLA deadlines
claims.appeal.internal.days=30
claims.appeal.external.days=3

# Escalation email
claims.escalation.supervisor.email=admin@meridian.local

# Slow query report
claims.slowquery.threshold.ms=500
claims.dba.email=dba@meridian.local

# Claim archive retention
claims.archive.retention.years=7

# Mail (must be enabled for escalation emails and slow query reports to be delivered)
claims.mail.enabled=false
claims.mail.from=noreply@meridian.local
claims.mail.smtp.host=localhost
claims.mail.smtp.port=25
```
