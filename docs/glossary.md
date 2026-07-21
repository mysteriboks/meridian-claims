# Meridian Claims — Domain Glossary

A plain-English reference for every significant domain term used in the system.
Definitions are self-contained; no links outside `docs/` are needed to understand them.
All monetary values in this system are `java.math.BigDecimal` in Java and `NUMERIC(12,2)`
in the database — never `double` or `float`.

---

## A

**Abandonment**
A claim in `PENDING_INFO` status that was never resolved before its information-request
due date. A nightly Quartz job scans for overdue `info_requests` records and automatically
transitions the claim to `ABANDONED`, a terminal state. The trigger is the
`info_requests.due_date` column, not a fixed calendar timer. Once abandoned, the claim
can no longer move forward; the member may re-submit a fresh claim if appropriate.

**Accumulator**
The running year-to-date total of how much a member has spent toward their deductible and
out-of-pocket maximum within a single benefit year. Stored in the `deductible_accumulators`
table, keyed by member, plan, and `benefit_year_start`. Every approved claim contributes to
the accumulator; voided or corrected claims reverse their contribution exactly using the
`claim_accumulator_contributions` ledger. Concurrent submissions for the same member are
serialized with `SELECT ... FOR UPDATE` so two claims cannot read the same balance
simultaneously.

**Adjudication**
The automated rule-based process that evaluates a claim and decides what the plan pays,
what the member owes, and whether the claim should be approved, denied, or sent to a human
reviewer. In Meridian Claims the adjudication engine runs a chain of 13 rules (6 hard, 7
soft) inside a single database transaction immediately after a claim is submitted. The
outcome and the reason for each rule's result are recorded in `adjudication_results`. See
also: HARD rule, SOFT rule, adjudication pipeline.

**Adjudication pipeline**
The ordered sequence of 13 rules executed by `AdjudicationService`. Hard rules run first
(steps 1–6); any hard-rule failure denies the claim immediately and stops the chain. Soft
rules (steps 7–13) then adjust financial amounts in a fixed order — network rate, fee
schedule, deductible, copay, benefit percentage, OOP cap, COB — before the engine computes
a final disposition. The ordering is deliberate: each rule depends on the result of the
ones before it.

**Allowed amount**
The dollar amount the plan agrees to recognize for a given procedure, as defined by the fee
schedule. The allowed amount is always less than or equal to the billed amount. All
financial calculations (deductible application, benefit percentage, member responsibility)
are computed against the allowed amount, not the billed amount. Stored as
`NUMERIC(12,2)` on `claim_line_items.allowed_amount`.

**Appeal — Internal**
A formal challenge by a member or staff against a denied claim, reviewed by a REVIEWER or
ADMIN within the same organization. The statutory deadline is 30 days from the denial date
by default (configurable). If upheld, the original claim is automatically re-adjudicated.
Tracked in the `appeals` table with `appeal_type = INTERNAL`.

**Appeal — External**
An appeal that escalates beyond the plan's internal review process, typically to an
independent third party or regulator. Carries a shorter deadline (72 hours for urgent
cases, configurable). Tracked in the `appeals` table with `appeal_type = EXTERNAL`.

**Auto-approve threshold**
A configurable dollar limit (set in `application.properties`) below which a claim that
passes all 13 adjudication rules is automatically moved to `APPROVED` without human review.
Claims at or above the threshold, or claims with other routing conditions, are instead sent
to `IN_REVIEW`. This is a safety valve that ensures large payments always get a human
second look.

---

## B

**Benefit calculation**
Adjudication rule 11 (a SOFT rule). After the deductible and copay have been applied, the
plan pays its contracted coverage percentage of the remaining allowed amount, and the member
owes the rest. The split is stored per line item. For out-of-network providers the
out-of-network coverage percentage applies; for in-network providers the in-network
percentage applies.

**Benefit percentage**
The share of the allowed amount that the plan covers after deductible and copay, expressed
as a percentage (e.g., 80%). Stored on the `plans` table as
`coverage_pct_in_network` and `coverage_pct_out_network`, and on `plan_coverage_rules` per
service type. Stored as `NUMERIC(5,2)` (a percentage, not a money column). See also:
benefit calculation, SOFT rule.

**Benefit year**
The 12-month window over which a member's deductible and out-of-pocket maximum accumulate.
Determined by the `benefit_year_start` date on the member's plan — employer groups may
renew on dates other than January 1st. A new accumulator row is created automatically the
first time a claim falls in a new benefit year; prior-year rows are kept for audit and
late-filing purposes.

**Billed amount**
The dollar amount a provider charges for a procedure, as entered by STAFF on the claim
submission form. Always `NUMERIC(12,2)`. The billed amount may exceed the allowed amount;
the plan only pays against the allowed amount. Stored on
`claim_line_items.billed_amount`.

---

## C

**CARC (Claim Adjustment Reason Code)**
A standardized three-digit code published by the Washington Publishing Company that
explains why a payment on a claim line item was adjusted or denied. Examples include codes
for "not covered," "deductible applied," or "COB adjustment." CARCs appear on remittance
advice so providers know exactly why each line was paid as it was. Stored on
`denial_reason_codes.carc_code` and on `remittance_batch_items.adjustment_reason_code`.

**Claim**
The primary business record in the system. A claim represents a request by a provider to
be reimbursed for one or more medical services delivered to a member on a specific date.
Each claim has a unique claim number, belongs to one member and one provider, carries one
or more diagnosis codes and one or more line items, and moves through a defined set of
statuses from `SUBMITTED` to a terminal state. Stored in the `claims` table; represented
in Java by `com.meridian.claims.model.Claim`.

**Claim audit**
A tamper-evident log of every status change, approval, denial, override, and view event on
a claim. Stored in the `claim_audit` table. Every action records who made the change, the
old and new status, a reason, and a timestamp. Separate from the general `audit_log` and
the PHI access log.

**Claim number**
A system-generated, human-readable identifier for a claim. Format: `CLM-YYYYMMDD-NNNNNN`
(date of generation followed by a six-digit sequence number). Generated by
`ClaimNumberGenerator` at submission time and stored on `claims.claim_number`. Immutable
after creation.

**Claim type**
One of three values on every claim — `ORIGINAL`, `CORRECTED`, or `VOID` — stored on
`claims.claim_type`. An ORIGINAL claim is a first-time submission. A CORRECTED claim
replaces a previously paid claim (linked via `original_claim_id`); when approved it
reverses the original's accumulator contribution and triggers a fresh adjudication. A VOID
claim cancels a previously paid claim entirely. See also: VOID/REPLACED.

**COB (Coordination of Benefits)**
The set of rules that govern how two insurance plans share the cost of a claim when a
member has both primary and secondary coverage. In Meridian Claims the COB adjudication
rule (rule 13, a SOFT rule) uses the non-duplication cap method: the secondary plan pays no
more than the lesser of (a) what it would normally pay, or (b) what remains of the allowed
amount after the primary plan has paid. The formula is:
`finalPlanPaid = min(normalPlanLiability, max(0, totalAllowed - cobPrimaryPaid))`.
Implemented in `CobAdjustmentRule`.

**Copay**
A fixed dollar amount the member pays per visit or service, applied after the deductible
has been satisfied. Stored on `plans.copay_amount` as `NUMERIC(12,2)`. Applied by
adjudication rule 10 (a SOFT rule, `CopayRule`) before the benefit percentage is
calculated. The copay reduces the member's remaining balance for benefit calculation
purposes.

**Coverage order**
Whether a member's plan is their primary or secondary insurance. Stored as `PRIMARY` or
`SECONDARY` on `member_coverage.coverage_order` and snapshotted onto
`claims.coverage_order` at submission. Claims with `SECONDARY` coverage order are always
routed to `IN_REVIEW` after adjudication (they do not auto-approve) and trigger the COB
adjustment rule.

**CPT code**
Current Procedural Terminology code. A five-character alphanumeric code published by the
American Medical Association that identifies a specific medical procedure or service.
CPT codes are AMA-copyrighted; the `procedure_codes` table ships empty and is populated
only by Admin staff with codes the organization is licensed to use. Free-text entry of CPT
codes on the claim form is not permitted — codes must come from the Admin-managed
`procedure_codes` master table. See also: fee schedule.

---

## D

**Date of service (DOS)**
The date on which the medical service was actually provided to the member. Used to
determine which benefit year applies, whether the claim was filed within the timely filing
window, and which fee schedule rate was in effect. Stored on `claims.date_of_service`.

**Deductible**
The total amount a member must pay out of pocket within a benefit year before the plan
begins paying its share. Stored on `plans.deductible_amount` as `NUMERIC(12,2)`. Applied
by adjudication rule 9 (a SOFT rule, `DeductibleRule`), which checks the member's
year-to-date accumulator and charges the member up to any remaining deductible balance.
Accumulator updates use `SELECT ... FOR UPDATE` to prevent double-counting under concurrent
submissions.

**Denial reason code**
A short code that explains why a claim was denied, selected from the
`denial_reason_codes` lookup table. Each denial reason code maps to a CARC code for
remittance reporting. Stored on `claims.denial_reason_code`. Reviewers must supply a denial
reason code when manually denying a claim; the adjudication engine sets one automatically
on a hard-rule failure.

---

## E

**Eligibility**
Whether a member was covered by a plan on the date of service. Checked by adjudication
rule 2 (a HARD rule, `EligibilityRule`), which looks up the member's `member_coverage`
records and confirms the date of service falls within an active coverage period. A claim
fails this rule if the member had no active coverage on that date, resulting in an
automatic `DENIED` disposition.

**EOB (Explanation of Benefits)**
A document generated automatically when a claim reaches `APPROVED` or `DENIED` status.
Explains to the member what was billed, what the plan allowed, what the plan paid, what
the member owes, and (if denied) why. Generated as printable HTML by `EobService`, stored
as text in `eob_documents`, and optionally emailed to the member. Finance and Staff can
view EOBs per member and mark them as mailed.

---

## F

**FHIR (Fast Healthcare Interoperability Resources)**
HL7's modern standard for healthcare data exchange. Meridian Claims accepts inbound claims in
**FHIR R4 Claim JSON** format — either a single `Claim` resource or a `Bundle` of `Claim`
resources. Only a documented subset of FHIR fields is mapped (member, provider, date of
service, diagnoses, line items, coverage order); all other FHIR fields are ignored. Parsed
by `FhirClaimFileParser` using Jackson; no heavyweight FHIR framework is used.

**Fee schedule**
A table of contracted allowed amounts per procedure code, per plan, optionally per provider,
with effective and termination dates. Resolved by `FeeScheduleService` during adjudication
rule 8 using the hierarchy: provider-specific rate, then plan-wide rate. If no rate is on
file, the line item is flagged `NO_RATE` and the claim is routed to manual review — it is
never silently zeroed. Stored in `fee_schedule_rates`. Historical rates are preserved so
re-adjudication of past claims uses the rate that was in effect at the time.

---

## H

**HARD rule**
An adjudication rule whose failure immediately denies the entire claim and stops the engine.
The six hard rules are: timely filing (1), member eligibility (2), duplicate claim (3),
procedure coverage (4), prior authorization (5), and referral (6). A HARD rule never
adjusts amounts — it either passes or kills the claim. All HARD rules run before any SOFT
rules.

**HIPAA**
The Health Insurance Portability and Accountability Act. The federal law that governs the
privacy and security of protected health information (PHI). In Meridian Claims, HIPAA
compliance shapes access control (minimum-necessary principle), PHI logging
(`phi_access_log`), log masking (DOB and member number are redacted), and the 7-year data
retention requirement.

---

## I

**Intake batch**
A single inbound claim file processed by the `InboundClaimFilePollerJob`. Each file receives
one row in `claim_intake_batches` (keyed by SHA-256 content hash for idempotency). The batch
records total records, succeeded, and quarantined counts, plus a status of `PROCESSING` /
`COMPLETED` / `FAILED`. Visible in **Admin → Intake Batches**.

**ISA control number**
The interchange control number from an X12 EDI file's ISA segment (ISA13). Stored on the
`claims.external_reference` column for EDI-submitted claims to enable reconciliation against
clearinghouse acknowledgements.

**ICD-10**
International Classification of Diseases, Tenth Revision. The standard coding system for
diagnoses. Each claim must carry at least one primary ICD-10 diagnosis code and may carry
up to eleven secondary codes, stored in the `claim_diagnoses` table. ICD-10-CM codes (the
clinical modification used in the US) are publicly available from CMS; a representative
subset is seeded via Flyway migration and the full set can be loaded as an append-only
migration. Diagnosis codes are selected from the Admin-managed `diagnosis_codes` master
table — free-text entry is not permitted.

**Info request**
A structured record created when a reviewer needs additional information before deciding on
a claim. Stored in `info_requests`. A reviewer must create an info request (specifying who
it is directed to — MEMBER, PROVIDER, or BOTH — and setting a due date) to move a claim
to `PENDING_INFO`; a free-text note alone is not sufficient. If the request is not answered
before the due date, the claim is automatically `ABANDONED`.

**In-network / Out-of-network**
Whether a provider has a contract with the plan. Stored on
`providers.network_status` as `IN_NETWORK` or `OUT_OF_NETWORK`. In-network claims use the
higher coverage percentage (`plans.coverage_pct_in_network`); out-of-network claims use the
lower one (`plans.coverage_pct_out_network`). Determined by adjudication rule 7
(`NetworkRule`, a SOFT rule).

---

## M

**Member**
An insured individual enrolled in a health plan. Identified by a system-generated member
number. A member may have multiple coverage records (e.g., primary plan through their own
employer and secondary plan through a spouse's employer). Stored in the `members` table;
soft-deleted on deactivation so historical claims remain intact.

**Member responsibility**
The portion of the allowed amount that the member must pay after the plan has applied
deductibles, copays, benefit percentage, and OOP cap adjustments. Stored per line item in
`claim_line_items.member_responsibility` as `NUMERIC(12,2)`. Summarized on the Payment
record as `member_responsibility_total`.

---

## N

**NO_RATE**
A marker applied to a claim line item when no fee schedule rate could be found for the
procedure, plan, and date combination. A `NO_RATE` flag causes the entire claim to be
routed to `IN_REVIEW` — the allowed amount is never silently set to zero. Stored on
`claim_line_items.rate_source`. Tracked in the Fee Schedule Coverage Report (Phase 7) to
identify gaps in the fee schedule.

**Non-duplication cap**
The COB method used by this system. The secondary plan's payment is capped so that the
combined payments from the primary and secondary plans do not exceed the total allowed
amount. See also: COB.

**NPI (National Provider Identifier)**
A unique ten-digit identification number assigned to every healthcare provider in the United
States by the Centers for Medicare and Medicaid Services (CMS). Stored on
`providers.npi`. STAFF enter the NPI when creating a provider record; the claim submission
form looks up providers by NPI. NPI is also included in payment batch export files sent to
the bank or ERP.

---

## O

**OOP max (Out-of-pocket maximum)**
The annual ceiling on how much a member can be required to pay toward covered services
within a benefit year. Once the member's year-to-date accumulator reaches the OOP max, the
plan covers 100% of allowed costs for the rest of the year. Applied by adjudication rule 12
(a SOFT rule, `OopMaxRule`): any member responsibility that would exceed the remaining OOP
budget is shifted to the plan instead. Stored on `plans.oop_max` as `NUMERIC(12,2)`.

**Optimistic locking**
A concurrency control technique used on the `claims` table. Each claim record carries a
`version` integer that is incremented on every update. If two users attempt to update the
same claim simultaneously, the second writer detects a stale version and receives a
user-friendly error rather than silently overwriting the first writer's changes.

---

## P

**Payment**
A financial record created for every approved claim, capturing the billed total, allowed
total, plan paid total, and member responsibility total (all `NUMERIC(12,2)`). Finance
marks a payment `PAID` and records an external reference number. Partial payments are
supported; the remaining balance is tracked. Stored in the `payments` table.

**Payment batch**
A group of `PENDING_PAYMENT` claims collected by Finance for a given date and exported
together as a flat CSV file for import into the bank or ERP system. Each line in the export
contains provider name, NPI, amount, and claim reference. When the batch is exported,
claims move to `PAID` and a remittance batch is generated simultaneously. Stored in
`payment_batches`; batch lifecycle: created → reviewed → `EXPORTED`.

**PHI (Protected Health Information)**
Any information that can be used to identify an individual and relates to their health,
health care, or payment for health care. Under HIPAA, PHI must be handled with strict
access controls. In Meridian Claims, every view of PHI is logged to `phi_access_log` (user,
member, claim, action, timestamp). Non-reviewer staff can only see claims they submitted or
are assigned to. PHI fields such as date of birth and member number are masked in
application log files.

**Plan**
An insurance product with a defined set of coverage rules, cost-sharing parameters, and a
contracted fee schedule. Stored in the `plans` table. Key fields include plan type
(HMO/PPO/EPO), deductible, OOP max, copay, in-network and out-of-network coverage
percentages, `benefit_year_start`, and `timely_filing_days`. Plan terms are snapshotted
onto each claim at submission so later plan edits do not retroactively change adjudicated
claims.

**Plan snapshot**
A copy of the plan identifier and coverage order recorded on a claim at the moment of
submission (`claims.plan_id`, `claims.coverage_order`). Because plan rules can change over
time, adjudication always uses the plan terms that were in effect when the claim was
submitted, not the current terms. This makes re-adjudication of historical claims
deterministic.

**Prior authorization**
A pre-approval that a provider must obtain from the plan before performing certain
procedures. When a plan's `plan_coverage_rules` record has `requires_prior_auth = true`,
adjudication rule 5 (a HARD rule, `PriorAuthRule`) checks that a valid prior authorization
exists for the member, procedure, and date of service. The authorization number is entered
on the claim submission form and stored on `claims.prior_auth_number`. Managed via the
`prior_authorizations` table.

**Provider**
A doctor, specialist, or medical facility that delivers services and submits claims for
reimbursement. Identified by NPI. Stored in the `providers` table with network status
(in-network or out-of-network), type (individual or facility), and specialty. Soft-deleted
on deactivation. Providers do not log in to the system in the current MVP — STAFF enter
claims on their behalf.

---

## R

**Referral**
An authorization from a primary care physician (PCP) for a member to see a specialist or
receive a specific service. Required for HMO plans when `plan_coverage_rules.requires_referral`
is true. Adjudication rule 6 (a HARD rule, `ReferralRule`) checks that a valid referral
exists. The referral number is entered on the claim submission form and stored on
`claims.referral_number`. Managed via the `referrals` table.

**Remittance advice**
A document sent to a provider explaining how a batch of claims was paid. Groups paid
claims by provider and lists each claim's procedure codes, billed amount, allowed amount,
plan paid amount, and the CARC code explaining any adjustment. Generated as printable HTML
by `RemittanceService` when Finance creates a payment batch. Stored in
`remittance_batches` and `remittance_batch_items`. Finance can download and mail or email
the remittance to each provider. An **X12 EDI 835** machine-readable version can also be
downloaded from the remittance batch detail screen via `Edi835Generator`.

---

## S

**SLA (Service Level Agreement)**
A configured time limit within which a claim must move from one status to the next. SLA
thresholds are defined in `application.properties`. When a claim stays in a status longer
than its SLA threshold, it is flagged visually in the reviewer worklist and a breach record
is written to `sla_breaches`. A Quartz job running hourly scans for breaches and sends
email escalation alerts to supervisors.

**SOFT rule**
An adjudication rule that adjusts the financial amounts on a claim and then allows the
engine to continue to the next rule. A soft rule failure does not deny the claim outright;
instead it affects how costs are split between the plan and the member, or it routes the
claim to manual review (e.g., the `NO_RATE` flag from `FeeScheduleRule`). The seven soft
rules are: network rate (7), fee schedule (8), deductible (9), copay (10), benefit
calculation (11), OOP max cap (12), and COB adjustment (13).

**Subrogation**
The right of the insurance plan to recover money it paid out on a claim from the party
actually responsible for the injury or illness — for example, a liable driver's auto
insurer after a car accident. A subrogation case is created automatically when an
accident-flagged claim is paid. Finance tracks the case, identifies the liable party, and
records any recovery amount. Recovery is plan income and does not affect the member's
deductible or OOP accumulators, which reflect what the member personally paid. Managed in
`subrogation_cases` by `SubrogationService`.

**Submission date**
The date a claim was entered into the system. Used together with the date of service to
calculate timely filing compliance. Stored on `claims.submission_date`.

---

## T

**Timely filing**
The requirement that a claim be submitted within a defined number of days after the date of
service. The deadline is configured per plan via `plans.timely_filing_days`. Adjudication
rule 1 (a HARD rule, `TimelyFilingRule`) computes the number of days between the date of
service and the submission date and denies the claim if the limit is exceeded. The deadline
varies by plan because different employer groups negotiate different filing windows.

---

## V

**VOID / REPLACED**
Terminal statuses that indicate a previously paid claim has been cancelled or superseded.
`VOIDED` means a VOID-type claim was submitted against the original, cancelling it and
reversing its accumulator contribution. `REPLACED` means a CORRECTED-type claim was
approved against the original, superseding it. Both transitions originate from the `PAID`
status and are guarded by the state machine. The new VOID claim record itself is created
already in a terminal `VOIDED` state and bypasses the adjudication engine; only the
original claim's transition to `VOIDED` is state-machine-guarded. See also: claim type,
`VoidReversalService`.

---

## W

**Worklist**
The reviewer queue — a filtered, paginated list of claims in states that require human
attention (`IN_REVIEW`, `PENDING_INFO`). Reviewers can filter by status, assignee, member,
provider, date range, and SLA breach flag. Claims can be assigned to a specific reviewer or
sit in the shared unassigned pool. The worklist also serves as the exception queue for
batch/EDI intake, since auto-adjudication routes only non-straight-through claims to
`IN_REVIEW`.

---

## X

**X12 EDI**
The ANSI X12 electronic data interchange standard used for healthcare transactions in the US.
Meridian Claims processes two transaction types:

- **837P (Professional)** and **837I (Institutional)** — inbound claim files from providers
  or clearinghouses. Parsed by `X12Edi837Parser` using the StAEDI streaming library.
  Each ST/SE transaction set maps to one claim. The ISA13 interchange control number is
  stored as `claims.external_reference` for reconciliation.

- **835 (Healthcare Claim Payment/Remittance Advice)** — outbound remittance advice
  generated by `Edi835Generator`. Produced from an existing `RemittanceBatch` and
  downloadable from the Finance → Remittance Batches screen.
