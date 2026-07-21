# Meridian Claims

Note: This is a sample "legacy" java application for testing. It is not intended to be upgraded or modifed. 

Meridian is a health-insurance **claims management** system — a server-rendered Java monolith that takes a claim from submission through adjudication, review, payment, and downstream workflow (appeals, subrogation, batch payment, scheduled operations).

---

## What it does

- **Claim intake & adjudication** — submit claims with diagnoses and line items; a 13-rule engine adjudicates (eligibility, coverage, prior-auth, referral, network, fee schedule, deductible, copay, benefit %, OOP cap, COB) and routes to auto-approve / auto-deny / manual review.
- **Review & payment** — reviewer worklist with assignment, SLA tracking and escalation; approve / deny / request-info; EOB generation; payment processing with partial payments; provider remittance advice.
- **Batch claim intake** — inbound FHIR R4 Claim JSON (`.json`) and X12 EDI 837P/837I (`.edi`/`.x12`/`.837`) files are polled from a directory every 5 minutes, parsed, and submitted through the same 13-rule adjudication engine as manual claims; per-record fault isolation, SHA-256 idempotency, and a real-time ledger in Admin → Intake Batches.
- **Outbound EDI 835** — X12 835 Healthcare Claim Payment/Remittance Advice files downloadable from the Finance → Remittance screen; optionally written to disk automatically.
- **Workflow & operations** — member appeals (with re-adjudication on approval), subrogation cases for accident claims, payment-batch CSV export, scheduled jobs (SLA escalation, stale-claim cleanup, benefit-year rollover, slow-query report, archiving), bulk re-adjudication, and member data export.

See [docs/architecture.md](docs/architecture.md) for the full capability map and architecture.

---

## Tech stack

| Concern | Choice |
| ------- | ------ |
| Language | Java 8 |
| Web | Spring MVC (XML config) + JSP / JSTL |
| Persistence | PostgreSQL via plain JDBC (`JdbcTemplate`), DAO pattern — no ORM |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| Money | `java.math.BigDecimal` / `NUMERIC(12,2)` |
| Scheduled jobs | Quartz (in-process) |
| Email | Spring `JavaMailSender` (dev-safe logging sender by default) |
| Build / packaging | Maven → WAR (`meridian-claims.war`) |
| App server | Tomcat 9 |
| Logging | Log4j |
| Tests | JUnit 4 + Mockito; H2 (PostgreSQL mode) for DAO integration tests |
| EDI parsing | StAEDI (streaming X12 reader/writer) |
| JSON parsing | Jackson (FHIR R4 Claim JSON) |

---

## Quick start

```bash
# 1. Build + run the test suite
mvn package

# 2. Provision PostgreSQL (Flyway migrates on startup)
createdb meridian_claims
createuser meridian --pwprompt   # use password: meridian
psql -c "GRANT ALL ON DATABASE meridian_claims TO meridian;"

# 3. Deploy the WAR to Tomcat (default local HTTP port 8090)
cp target/meridian-claims.war "$CATALINA_HOME/webapps/"

# 4. Smoke test
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/meridian-claims/health   # → 200
```

Database connection and pool sizing are configured in `src/main/resources/db.properties`; app settings (SLA thresholds, job crons, mail, thresholds) in `src/main/resources/application.properties`. See [docs/configuration.md](docs/configuration.md) for the full key reference.

**Production deployment** runs the same WAR under the `prod` profile — start Tomcat with `-Dclaims.profile=prod` (default is `dev`, which keeps plain-HTTP local runnability on port 8090). The prod profile activates HTTPS enforcement, Secure/HSTS cookies, env-var DB credentials, terse error pages, and mail alerts. Prod overrides are read from an external `$CATALINA_BASE/conf/meridian-claims-prod.properties` (template: [docs/meridian-claims-prod.properties.sample](docs/meridian-claims-prod.properties.sample)) — this file is never packaged in the WAR so production credentials stay off the classpath. See [docs/getting-started.md → Production Deployment](docs/getting-started.md#production-deployment) for the full step-by-step procedure.

---

## Documentation

### New developer — get running locally

1. [docs/getting-started.md](docs/getting-started.md) — prerequisites, database setup, build, deploy, first-run admin setup
2. [docs/architecture.md](docs/architecture.md) — four-layer design, filter chain, transaction model, Mermaid diagrams
3. [docs/adjudication.md](docs/adjudication.md) — the 13-rule engine and money calculation (essential for working on claims)
4. [docs/data-model.md](docs/data-model.md) — full schema, ER diagrams, claim state machine

### Business user / operator

- [docs/user-guide.md](docs/user-guide.md) — every screen and workflow, organised by role (STAFF / REVIEWER / FINANCE / ADMIN / ANALYST)
- [docs/glossary.md](docs/glossary.md) — plain-English definitions for domain terms (COB, EOB, CARC, OOP max, etc.)

### Deploying to production

1. [docs/getting-started.md → Production Deployment](docs/getting-started.md#production-deployment) — pre-deploy steps, deploy commands, smoke test, rollback, first-deploy operational setup
2. [docs/production-hardening.md](docs/production-hardening.md) — production-hardening playbook (dev/prod profile, security, observability, resilience); re-apply when adding a new ingress surface
3. [docs/configuration.md](docs/configuration.md) — every config key, prod overrides, credentials handling
4. [docs/operations.md](docs/operations.md) — monitoring, log locations, database backup, incident response

### Reference

| Doc | Use when you need to… |
| --- | --------------------- |
| [docs/api-and-screens.md](docs/api-and-screens.md) | Find a route, its HTTP method, or which role can access it |
| [docs/scheduled-jobs.md](docs/scheduled-jobs.md) | Check a job's schedule, trigger it manually, or read its run history |
| [docs/security.md](docs/security.md) | Understand AuthN/Z, CSRF, PHI logging, or the audit trail |
| [docs/data-model.md](docs/data-model.md) | Look up a table's columns, FKs, or the migration that created it |
| [docs/glossary.md](docs/glossary.md) | Decode a domain term you haven't seen before |

---

## Architecture at a glance

Strict four-layer monolith; dependencies point downward only:

```text
Controller (Spring MVC) → Service → DAO → PostgreSQL
```

Controllers never touch JDBC; DAOs hold no business rules; business logic lives in services. No microservices, no message queues. See [docs/architecture.md](docs/architecture.md) for details.

---

## Project layout

```text
src/main/java/com/meridian/claims/
  model/       POJOs (Claim, Member, Provider, Plan, Payment, Appeal, …)
  dao/         DAO interfaces + JDBC implementations
  service/     business logic + adjudication rules (service/adjudication/)
  controller/  Spring MVC controllers
  web/         servlet filters (SecurityFilter, RoleFilter, CsrfFilter, …)
  job/         Quartz scheduled jobs
  util/        helpers (Money, Page, CsvWriter, HtmlUtil, ValidationUtil, …)
src/main/resources/db/migration/   Flyway migrations (V1…Vn)
src/main/webapp/WEB-INF/           web.xml, Spring XML, JSP views
src/test/                          JUnit/Mockito tests + H2 migration stubs
samples/                           seed dataset + FHIR/X12 intake examples (see samples/README.md)
```

---

## Contributing

This codebase follows a deliberate **legacy-enterprise Java style**: explicit getters/setters, `for` loops over streams, plain SQL in DAOs, XML Spring config, and verbose-over-clever as a guiding principle. Key rules: one phase at a time, nothing optional, lean/no-duplication, and a per-phase redundancy sweep before any phase is marked complete.
