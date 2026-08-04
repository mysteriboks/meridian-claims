# Routes & Screens — Meridian Claims

Developer reference for every HTTP endpoint in the application. All routes render
server-side HTML via JSP (no REST/JSON API). Organized by module. Role enforcement
is a combination of `SecurityFilter` (session check), `RoleFilter` (path-prefix
rules), and per-controller inline guards.

Role values: `ADMIN`, `REVIEWER`, `FINANCE`, `STAFF`, `ANALYST`.
Path-prefix role gates from `RoleFilter`: `/admin/**` → ADMIN; `/finance/**` → FINANCE or ADMIN.
All other authenticated routes require a valid session; `SecurityFilter` redirects
unauthenticated requests to `/login`.

---

## Special / System Endpoints

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/health` | Public (no auth) | Renders DB reachability and DBCP2 pool stats. Returns HTTP 200 when healthy, HTTP 503 when the database is unreachable. For use by load balancers and monitoring. |
| GET | `/login` | Public | Renders the login form. |
| POST | `/login` | Public | Processes credentials. On success redirects to `/dashboard`; if password expired or force-reset is set redirects to `/password/change`. On failure renders the login form with an error message. Locks account after repeated failures. |
| POST | `/logout` | Authenticated | Invalidates the session and redirects to `/login?loggedOut`. |
| GET | `/password/change` | Authenticated | Renders the change-password form. Shown automatically when a session user has an expired password or `force_reset` flag. |
| POST | `/password/change` | Authenticated | Validates current password, complexity rules, and confirmation match; updates the hash; refreshes the session user object; redirects to `/dashboard?passwordChanged`. |
| GET | `/dashboard` | Authenticated | Summary dashboard: claim counts by status, today/week submission counts, recent activity feed, top denial reasons, payment totals. REVIEWER/ADMIN additionally see their personal queue size and SLA breach count. |

---

## Claims

Base path: `/claims` — Controller: `ClaimController`

Role enforcement: REVIEWER and ADMIN see all claims. Other roles see only claims they
submitted or are assigned to (HIPAA minimum-necessary scoping applied in the worklist
query). Reviewer actions (approve, deny, request-info, assign, resubmit, re-adjudicate)
require REVIEWER or ADMIN.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/claims` | Authenticated | Paginated claim worklist. Accepts query params: `q` (free-text search), `status` (ClaimStatus enum), `assignee` (user ID), `unassigned=true`, `slaBreached=true`, `page`. Returns 20 results per page. Non-reviewer roles are scoped to their own claims. |
| GET | `/claims/new` | Authenticated | Renders claim submission form with dropdowns for members, providers, procedure codes, diagnosis codes, and claim type. |
| POST | `/claims` | Authenticated | Submits a new claim. Validates member, provider, date of service, line items (procedure code + billed amount), and at least one diagnosis code. On success redirects to `/claims/{id}`; on error re-renders the form. |
| GET | `/claims/{id}` | Authenticated | Claim detail view. Checks `canViewClaim` permission; records a PHI access log entry on every authorized view. Displays line items, diagnoses, adjudication results, audit trail, info requests, notes, and SLA breach status. |
| POST | `/claims/{id}/approve` | REVIEWER, ADMIN | Approves the claim with a required `notes` param. Triggers the approval state transition via `ClaimService`. Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/deny` | REVIEWER, ADMIN | Denies the claim. Requires `denialReasonCode` and `notes`. Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/request-info` | REVIEWER, ADMIN | Creates an info request on the claim. Requires `requestedFrom`, `dueDate` (yyyy-MM-dd), and `requestNotes`. Transitions status to PENDING_INFO. Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/resubmit` | REVIEWER, ADMIN | Moves the claim back to IN_REVIEW from PENDING_INFO. Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/assign` | REVIEWER, ADMIN | Assigns or unassigns the claim to a reviewer. Param: `reviewerId` (optional; omit to unassign). Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/notes` | Authenticated | Appends a freetext note to the claim's note log. Param: `note`. Redirects to `/claims/{id}`. |
| POST | `/claims/{id}/readjudicate` | REVIEWER, ADMIN | Re-runs the adjudication pipeline on the claim. Updates adjudication results in place. Redirects to `/claims/{id}`. |
| POST | `/claims/info-requests/{irId}/respond` | Authenticated | Records a response to an open info request. Requires `claimId` (for redirect) and `responseNotes`. Redirects to `/claims/{claimId}`. |

---

## Members

Base path: `/members` — Controller: `MemberController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/members` | Authenticated | Paginated member list. Accepts `q` (search) and `page`. Returns 20 per page. |
| GET | `/members/new` | Authenticated | Renders create-member form with MemberStatus enum values. |
| POST | `/members/new` | Authenticated | Creates a member. Required: `memberNumber`, `firstName`, `lastName`. Optional: `dob` (yyyy-MM-dd), `address`, `phone`, `email`. Redirects to `/members/{id}` on success. |
| GET | `/members/{id}` | Authenticated | Member detail view. Displays demographic data, coverage records, inline coverage-add form (plan, coverage order, effective/termination dates), and the last 10 rows of eligibility check history. |
| GET | `/members/{id}/edit` | Authenticated | Renders edit form pre-populated with member data. |
| POST | `/members/{id}/edit` | Authenticated | Updates member demographics and status. Same validation as create (memberNumber not re-validated). Redirects to `/members/{id}`. |
| POST | `/members/{id}/deactivate` | Authenticated | Deactivates the member. Redirects to `/members`. |
| POST | `/members/{id}/coverage/add` | Authenticated | Adds a MemberCoverage record. Requires `planId`, `coverageOrder` (PRIMARY/SECONDARY), `effectiveDate`. Optional: `terminationDate`. Redirects to `/members/{id}`. |
| POST | `/members/{id}/coverage/{coverageId}/remove` | Authenticated | Removes a specific coverage record by its ID. Redirects to `/members/{id}`. |
| POST | `/members/{id}/eligibility/check` | Authenticated | Runs a real-time X12 270/271 eligibility check (Phase 16) via `EligibilityCheckService`. Optional: `providerId`, `serviceType` (default `30`). Flashes the result (`ACTIVE`/`INACTIVE`/error) and redirects to `/members/{id}`. |

---

## Providers

Base path: `/providers` — Controller: `ProviderController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/providers` | Authenticated | Paginated provider list. Accepts `q` and `page`. Returns 20 per page. |
| GET | `/providers/new` | Authenticated | Renders create-provider form with ProviderType and NetworkStatus enum values. |
| POST | `/providers/new` | Authenticated | Creates a provider. Required: `npi` (validated format), `name`, `providerType`, `networkStatus`. Optional: `specialty`, `phone`, `address`. Redirects to `/providers/{id}`. |
| GET | `/providers/{id}` | Authenticated | Provider detail view. Shows ACH banking info (masked account number) if configured. |
| GET | `/providers/{id}/edit` | Authenticated | Renders edit form pre-populated with provider data. |
| POST | `/providers/{id}/edit` | Authenticated | Updates provider record. Same field set as create. Redirects to `/providers/{id}`. |
| POST | `/providers/{id}/deactivate` | Authenticated | Deactivates the provider. Redirects to `/providers`. |
| POST | `/providers/{id}/banking` | Authenticated | Sets or clears the provider's ACH disbursement banking info (Phase 18). Optional: `achRoutingNumber` (9 digits), `achAccountNumber`, `achAccountType` (`CHECKING`/`SAVINGS`). All blank clears the banking info. Redirects to `/providers/{id}`. |

---

## Appeals

Base path: `/appeals` — Controller: `AppealController`

Submitting an appeal requires STAFF or above. Approving/denying requires REVIEWER or
ADMIN. Withdrawing requires any authenticated user.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/appeals` | Authenticated | Lists all open appeals with their deadline dates. |
| GET | `/appeals/{id}` | Authenticated | Appeal detail view. |
| POST | `/appeals` | Authenticated | Submits an appeal on a denied claim. Required: `claimId`, `appealType`. Computes deadline from plan timely-filing rules. Redirects to `/appeals/{id}`. |
| POST | `/appeals/{id}/approve` | REVIEWER, ADMIN | Approves the appeal and triggers claim re-adjudication. Requires `outcomeNotes`. Redirects to `/appeals/{id}`. |
| POST | `/appeals/{id}/deny` | REVIEWER, ADMIN | Denies the appeal with outcome notes. Requires `outcomeNotes`. Redirects to `/appeals/{id}`. |
| POST | `/appeals/{id}/withdraw` | Authenticated | Withdraws an open appeal. Redirects to `/appeals/{id}`. |

---

## Prior Authorizations

Base path: `/prior-auth` — Controller: `PriorAuthorizationController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/prior-auth` | Authenticated | Paginated list of all prior authorizations. Optional `memberId` param to filter by member. Returns 20 per page. |
| GET | `/prior-auth/new` | Authenticated | Renders create form. Optional `memberId` query param pre-selects a member. Dropdowns for members, providers, service types. |
| POST | `/prior-auth/new` | Authenticated | Creates a prior authorization. Required: `memberId`, `providerId`, `procedureCode`, `authorizedFrom`, `authorizedTo`. Optional: `serviceType`, `approvedUnits` (default 1), `notes`. Redirects to `/prior-auth/{id}`. |
| GET | `/prior-auth/{id}` | Authenticated | Prior authorization detail view with all PriorAuthStatus values. |
| GET | `/prior-auth/{id}/edit` | Authenticated | Renders edit form pre-populated with authorization data. |
| POST | `/prior-auth/{id}/edit` | Authenticated | Updates the authorization. Fields: `procedureCode`, `serviceType`, `authorizedFrom`, `authorizedTo`, `status`, `approvedUnits`, `notes`. Redirects to `/prior-auth/{id}`. |
| POST | `/prior-auth/{id}/expire` | Authenticated | Expires the authorization immediately. Redirects to `/prior-auth/{id}`. |

---

## Referrals

Base path: `/referrals` — Controller: `ReferralController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/referrals` | Authenticated | Paginated referral list. Optional `memberId` param to filter. Returns 20 per page. |
| GET | `/referrals/new` | Authenticated | Renders create form. Optional `memberId` param pre-selects a member. Dropdowns for members, providers, service types. |
| POST | `/referrals/new` | Authenticated | Creates a referral. Required: `memberId`, `referringProviderId`, `referredToProviderId`, `serviceType`, `validFrom`, `validTo`. Optional: `notes`. Redirects to `/referrals/{id}`. |
| GET | `/referrals/{id}` | Authenticated | Referral detail view with ReferralStatus values. |
| GET | `/referrals/{id}/edit` | Authenticated | Renders edit form pre-populated with referral data. |
| POST | `/referrals/{id}/edit` | Authenticated | Updates the referral. Fields: `serviceType`, `validFrom`, `validTo`, `status`, `notes`. Redirects to `/referrals/{id}`. |
| POST | `/referrals/{id}/expire` | Authenticated | Expires the referral immediately. Redirects to `/referrals/{id}`. |

---

## Finance

Base path: `/finance` — Controller: `FinanceController`

All `/finance/**` routes require FINANCE or ADMIN role (enforced by `RoleFilter`).

### Payments

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/payments` | FINANCE, ADMIN | Lists all pending payments. |
| GET | `/finance/payments/{id}` | FINANCE, ADMIN | Payment detail view. |
| POST | `/finance/payments/{id}/mark-paid` | FINANCE, ADMIN | Records a payment as paid. Required: `referenceNumber`, `paymentDate` (yyyy-MM-dd), `amountPaid`. Redirects to `/finance/payments`. |

### EOB Documents

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/eobs` | FINANCE, ADMIN | Lists EOB documents. Optional `memberId` query param to filter by member. |
| GET | `/finance/eobs/{id}` | FINANCE, ADMIN | EOB document view. |
| POST | `/finance/eobs/{id}/mark-mailed` | FINANCE, ADMIN | Marks an EOB as physically mailed. Records the acting user and timestamp. Redirects to `/finance/eobs/{id}`. |

### Remittance Batches

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/remittance` | FINANCE, ADMIN | Lists all remittance batches alongside currently pending payments. |
| POST | `/finance/remittance/generate` | FINANCE, ADMIN | Generates a new remittance batch. Required: `paymentIds` (comma-separated integers), `paymentDate` (yyyy-MM-dd). Redirects to `/finance/remittance/{id}`. |
| GET | `/finance/remittance/{id}` | FINANCE, ADMIN | Remittance batch detail view including rendered HTML of the batch document. |
| GET | `/finance/remittance/{id}/835` | FINANCE, ADMIN | Streams the remittance batch as an X12 835 EDI file (`application/EDI-X12`). Content-Disposition triggers download as `remittance-{id}.835`. If `claims.remittance.edi.output.path` is configured, the file is also written to disk. If the paired payment batch was paid electronically (Phase 18), TRN02 carries the EFT reassociation number and BPR04 reads `ACH` instead of `CHK`. |
| POST | `/finance/remittance/{id}/mark-sent` | FINANCE, ADMIN | Marks a remittance batch as sent. Redirects to `/finance/remittance/{id}`. |

### Payment Batches

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/batches` | FINANCE, ADMIN | Lists all payment batches alongside pending payments. |
| POST | `/finance/batches/create` | FINANCE, ADMIN | Creates a new payment batch from all currently pending payments. Required: `batchDate` (yyyy-MM-dd). Redirects to `/finance/batches/{id}`. |
| GET | `/finance/batches/{id}` | FINANCE, ADMIN | Payment batch detail view. Shows EFT/ACH status (Phase 18) when at least one provider in the batch was paid electronically. |
| POST | `/finance/batches/{id}/export` | FINANCE, ADMIN | Exports the batch as CSV. Stores the CSV content as a flash attribute `csvContent`. Also attempts EFT/ACH issuance (Phase 18) — see `EftPaymentService`. Redirects to `/finance/batches/{id}`. |
| GET | `/finance/batches/{id}/ach` | FINANCE, ADMIN | Downloads the NACHA ACH file for this batch, regenerated on demand from the persisted TRN reassociation number and current provider banking data. 404 if no EFT payment exists for this batch (batch was entirely check-paid). |
| POST | `/finance/batches/{id}/eft/settle` | FINANCE, ADMIN | Marks the batch's EFT payment as `SETTLED` (manual confirmation — no live bank feed). Redirects to `/finance/batches/{id}`. |

### Subrogation

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/subrogation` | FINANCE, ADMIN | Lists all open subrogation cases. |
| GET | `/finance/subrogation/{id}` | FINANCE, ADMIN | Subrogation case detail view. |
| POST | `/finance/subrogation/{id}/recover` | FINANCE, ADMIN | Records a recovery on a subrogation case. Required: `liableParty`, `recoveryAmount`. Optional: `notes`. Redirects to `/finance/subrogation/{id}`. |

### Member Export (CSV downloads)

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/finance/member-export` | FINANCE, ADMIN | Renders the member export form. |
| GET | `/finance/member-export/claims` | FINANCE, ADMIN | Streams a CSV file (`text/csv`) of all claims for `memberId`. Content-Disposition triggers download as `member-{id}-claims.csv`. |
| GET | `/finance/member-export/payments` | FINANCE, ADMIN | Streams a CSV file of all payments for `memberId`. Download filename: `member-{id}-payments.csv`. |

---

## Reports

Base path: `/reports` — Controller: `ReportController`

Report routes are readable by ANALYST, REVIEWER, FINANCE, and ADMIN roles. **STAFF is blocked**
by `RoleFilter` (aggregate report data exceeds HIPAA minimum-necessary access for that role).
Each report supports an optional `?export=csv` query parameter that bypasses the JSP view and
streams a `text/csv` download directly.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/reports` | Authenticated | Report index page listing all available reports. |
| GET | `/reports/claims-summary` | Authenticated | Aggregated claim counts and amounts by status/plan. Params: `from`, `to` (yyyy-MM-dd; default last 3 months), `planId`. Export: `?export=csv`. |
| GET | `/reports/claims-detail` | Authenticated | Paginated claim-level detail rows. Params: `from`, `to`, `status`, `planId`, `providerId`, `page` (50 per page). Export streams all rows without pagination. |
| GET | `/reports/denials` | Authenticated | Denial counts and amounts by denial reason code. Params: `from`, `to` (default last 3 months). Export: `?export=csv`. |
| GET | `/reports/payments` | Authenticated | Payment totals by plan/provider. Params: `from`, `to` (default last month), `planId`, `providerId`. Export: `?export=csv`. |
| GET | `/reports/member-activity` | Authenticated | Claim and payment history for a single member. Param: `memberId` (required to populate data). Export: `?export=csv` (only when memberId provided). |
| GET | `/reports/provider-activity` | Authenticated | Claim and payment history for a single provider. Param: `providerId` (required to populate data). Export: `?export=csv` (only when providerId provided). |
| GET | `/reports/sla-performance` | Authenticated | SLA compliance metrics by reviewer. Params: `from`, `to` (default last month). Export: `?export=csv`. |
| GET | `/reports/adjudication-rules` | Authenticated | Hit/miss counts per adjudication rule. Params: `from`, `to` (default last 3 months). Export: `?export=csv`. |
| GET | `/reports/appeals` | Authenticated | Appeal outcomes by type and plan. Params: `from`, `to` (default last 3 months). Export: `?export=csv`. |
| GET | `/reports/cob` | Authenticated | Coordination of benefits cases. Params: `from`, `to` (default last 3 months). Export: `?export=csv`. |
| GET | `/reports/subrogation` | Authenticated | Subrogation case summary (no date filter). Export: `?export=csv`. |
| GET | `/reports/fee-schedule-coverage` | Authenticated | Procedure codes without a fee schedule rate on any active plan. Export: `?export=csv`. |

---

## Admin — Plans

Base path: `/admin/plans` — Controller: `PlanController`

All `/admin/**` routes require ADMIN role (`RoleFilter`).

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/plans` | ADMIN | Paginated plan list. Returns 20 per page. |
| GET | `/admin/plans/new` | ADMIN | Renders create-plan form with PlanType enum values and service type categories. |
| POST | `/admin/plans/new` | ADMIN | Creates a plan. Required: `planName`, `planType`, `deductibleAmount`, `oopMax`, `copayAmount`, `coveragePctInNetwork`, `coveragePctOutNetwork`, `benefitYearStart` (yyyy-MM-dd), `timelyFilingDays`. All monetary fields are BigDecimal. Redirects to `/admin/plans/{id}`. |
| GET | `/admin/plans/{id}` | ADMIN | Plan detail view with coverage rules list and inline rule-add form. |
| GET | `/admin/plans/{id}/edit` | ADMIN | Renders edit form pre-populated with plan data. |
| POST | `/admin/plans/{id}/edit` | ADMIN | Updates the plan. Same field set as create. Redirects to `/admin/plans/{id}`. |
| POST | `/admin/plans/{id}/deactivate` | ADMIN | Deactivates the plan. Redirects to `/admin/plans`. |
| POST | `/admin/plans/{id}/rules/save` | ADMIN | Upserts a per-service-type coverage rule on the plan. Required: `serviceType`, `coveragePct`. Optional: `requiresReferral` (default false), `requiresPriorAuth` (default false). Redirects to `/admin/plans/{id}`. |
| POST | `/admin/plans/{id}/rules/{ruleId}/delete` | ADMIN | Deletes a specific coverage rule by its ID. Redirects to `/admin/plans/{id}`. |

---

## Admin — Fee Schedule

Base path: `/admin/fee-schedule` — Controller: `FeeScheduleController`

All routes require `planId` query param for list/create operations. ADMIN only.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/fee-schedule` | ADMIN | Paginated fee schedule rates for a plan. Required query param: `planId`. Returns 25 per page. |
| GET | `/admin/fee-schedule/new` | ADMIN | Renders create-rate form. Required query param: `planId`. Dropdowns for active providers. |
| POST | `/admin/fee-schedule/new` | ADMIN | Creates a fee schedule rate. Required: `planId`, `procedureCode`, `allowedAmount`, `effectiveDate` (yyyy-MM-dd). Optional: `providerId` (null = applies to all providers on the plan), `terminationDate`. Redirects to `/admin/fee-schedule?planId={planId}`. |
| GET | `/admin/fee-schedule/{id}/edit` | ADMIN | Renders edit form pre-populated with rate data. |
| POST | `/admin/fee-schedule/{id}/edit` | ADMIN | Updates `allowedAmount`, `effectiveDate`, and `terminationDate`. Redirects to `/admin/fee-schedule?planId={planId}`. |
| POST | `/admin/fee-schedule/{id}/expire` | ADMIN | Sets `terminationDate` on a rate to expire it. Required: `terminationDate` (yyyy-MM-dd). Redirects to `/admin/fee-schedule?planId={planId}`. |

---

## Admin — Lookup Codes

Base path: `/admin/lookups` — Controller: `LookupController`

All routes require ADMIN. Lookups are served from in-memory cache; the refresh
endpoint reloads all caches from the database.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| POST | `/admin/lookups/refresh` | ADMIN | Reloads all lookup caches from the database. Optional `returnTo` param controls redirect target (default: `/admin/lookups/service-types`). |
| GET | `/admin/lookups/denial-reasons` | ADMIN | Lists all denial reason codes. |
| POST | `/admin/lookups/denial-reasons/save` | ADMIN | Creates or updates a denial reason code. Required: `code`, `description`. Optional: `carcCode`, `active` (default true). Redirects to `/admin/lookups/denial-reasons`. |
| GET | `/admin/lookups/procedure-codes` | ADMIN | Lists all procedure codes (CPT). |
| POST | `/admin/lookups/procedure-codes/save` | ADMIN | Creates or updates a procedure code. Required: `code`, `description`. Optional: `serviceType`, `active`. Redirects to `/admin/lookups/procedure-codes`. |
| GET | `/admin/lookups/diagnosis-codes` | ADMIN | Lists all ICD-10 diagnosis codes. |
| POST | `/admin/lookups/diagnosis-codes/save` | ADMIN | Creates or updates a diagnosis code. Required: `code`, `description`. Optional: `active`. Redirects to `/admin/lookups/diagnosis-codes`. |
| GET | `/admin/lookups/service-types` | ADMIN | Lists all service type categories. |
| POST | `/admin/lookups/service-types/save` | ADMIN | Creates or updates a service type category. Required: `code`, `description`. Optional: `active`. Redirects to `/admin/lookups/service-types`. |

---

## Admin — Users

Base path: `/admin/users` — Controller: `UserController`

All routes require ADMIN.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/users` | ADMIN | Lists all users with their roles. |
| GET | `/admin/users/new` | ADMIN | Renders create-user form with UserRole enum values. |
| POST | `/admin/users/new` | ADMIN | Creates a user. Required: `username`, `fullName`, `role`, `tempPassword`. Redirects to `/admin/users` on success. |
| GET | `/admin/users/{id}/edit` | ADMIN | Renders edit form pre-populated with user data. |
| POST | `/admin/users/{id}/edit` | ADMIN | Updates `fullName` and `role`. Redirects to `/admin/users`. |
| POST | `/admin/users/{id}/deactivate` | ADMIN | Deactivates the user account. Redirects to `/admin/users`. |
| POST | `/admin/users/{id}/activate` | ADMIN | Reactivates a previously deactivated account. Redirects to `/admin/users`. |
| POST | `/admin/users/{id}/unlock` | ADMIN | Clears the account lockout (failed-attempt counter reset). Redirects to `/admin/users`. |
| POST | `/admin/users/{id}/force-reset` | ADMIN | Sets `force_reset` flag; user is required to change password on next login. Redirects to `/admin/users`. |

---

## Admin — Operations

Base path: `/admin/operations` — Controller: `AdminOperationsController`

All routes require ADMIN.

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/operations` | ADMIN | Operational dashboard: last 20 scheduled-job log entries and the list of manually triggerable jobs. |
| POST | `/admin/operations/bulk-readjudicate` | ADMIN | Re-adjudicates all claims in a given status. Required: `status` (ClaimStatus value). Redirects to `/admin/operations` with count of reprocessed claims. |
| POST | `/admin/operations/trigger-job` | ADMIN | Manually triggers a Quartz job by name. `jobName` must be one of: `slaEscalationJobDetail`, `staleClaimJobDetail`, `benefitYearRolloverJobDetail`, `appealSlaEscalationJobDetail`, `slowQueryReportJobDetail`, `claimArchiveJobDetail`, `inboundClaimFilePollerJobDetail`, `tradingPartnerPollerJobDetail`. Redirects to `/admin/operations`. |
| GET | `/admin/operations/archive` | ADMIN | Searches the claim archive. Accepts `q` free-text param. Returns matching archived claims. |

---

## Admin — Intake Batches

Base path: `/admin/intake-batches` — Controller: `IntakeBatchController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/intake-batches` | ADMIN | Read-only monitor of the batch claim intake ledger. Shows file name, status (COMPLETED/PARTIAL/FAILED), total/succeeded/quarantined counts, processed timestamp, and quarantine error notes. Accepts optional `limit` query param (default 50, max 200). |

---

## Admin — Enrollment Batches

Base path: `/admin/enrollment-batches` — Controller: `EnrollmentBatchController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/enrollment-batches` | ADMIN | Read-only monitor of the X12 834 enrollment file ledger (`enrollment_batches`, Phase 17). Same shape as Intake Batches: file name, status, total/succeeded/quarantined counts, processed timestamp, and quarantine error notes. Accepts optional `limit` query param (default 50, max 200). |

---

## Admin — Integrations

Base path: `/admin/integrations` — Controller: `IntegrationController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/integrations` | ADMIN | Read-only monitor of the EDI transaction log (`edi_transactions`) — every inbound 837 interchange and its outbound acknowledgments (999, 277CA, TA1), including which trading partner (if any) each row is attributed to. Accepts optional `limit` query param (default 50, max 200). |

---

## Admin — Trading Partners

Base path: `/admin/trading-partners` — Controller: `TradingPartnerController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/trading-partners` | ADMIN | Lists all trading partners (active and inactive) with their transport type and inbound path. |
| GET | `/admin/trading-partners/new` | ADMIN | Renders the create-partner form. |
| POST | `/admin/trading-partners/new` | ADMIN | Creates a partner. Required: `partnerName`, `isaQualifier`, `isaId`, `gsId`, `transportType` (`LOCAL`/`SFTP`), `inboundPath`. SFTP additionally requires `transportHost`, `transportUsername`, `transportCredentialRef` (a key name — the actual password is never entered here; it's set separately in the external prod overlay). Redirects to `/admin/trading-partners`. |
| GET | `/admin/trading-partners/{id}/edit` | ADMIN | Renders the edit form pre-populated with the partner's data. |
| POST | `/admin/trading-partners/{id}/edit` | ADMIN | Updates the partner. Same field set as create. Redirects to `/admin/trading-partners`. |
| POST | `/admin/trading-partners/{id}/deactivate` | ADMIN | Deactivates the partner — `TradingPartnerPollerJob` stops polling it. Redirects to `/admin/trading-partners`. |

---

## Admin — Audit Log

Base path: `/admin/audit` — Controller: `AuditViewController`

| Method | Path | Role | Description |
| -------- | ------ | ------ | ------------- |
| GET | `/admin/audit` | ADMIN | Paginated, searchable audit log viewer. Params: `username`, `eventType`, `entityType`, `from` (yyyy-MM-dd), `to` (yyyy-MM-dd), `page`. Returns 50 entries per page. Non-ADMIN sessions are redirected to `/dashboard` with an error flash. |

---

## Notes on Cross-Cutting Behavior

**CSRF protection.** `CsrfFilter` validates a token on every state-changing POST. The
token is placed in the session and must be included as a hidden field `_csrf` in every
form. GETs are not checked.

**PHI access logging.** `ClaimController#view` records a PHI access log entry on every
authorized claim detail view via `PhiAccessLogService`. The log is separate from the
general audit log and is not exposed through the admin UI.

**Flash attributes.** All POST handlers use `RedirectAttributes` to pass `success` or
`error` strings through the redirect. JSP layouts display these as banner messages.

**Date format.** All date parameters are expected in `yyyy-MM-dd` format. `ClaimController`
registers a `PropertyEditorSupport` for `java.util.Date`; other controllers use
`@DateTimeFormat(pattern = "yyyy-MM-dd")`.

**Pagination.** The `Page<T>` utility is used throughout. Default page sizes are 20
(most lists), 25 (fee schedule), and 50 (reports and audit log).
