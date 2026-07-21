# Sample Files

Example data and intake files for local testing and demos. **None of these are loaded
automatically** — the application ships with no seeded data. Load `sample_data.sql` first;
the intake files reference the members and provider it creates.

| File | Purpose |
| ---- | ------- |
| `sample_data.sql` | Seed dataset — members (`MBR-001`…), provider (NPI `1234567890`), a plan with coverage rules, fee-schedule rates, and a few claims. Gives the dashboard and reports real rows to render. |
| `sample_intake_bundle.json` | FHIR R4 `Bundle` of `Claim` resources for the Phase 9 batch intake poller. References `MBR-001` and NPI `1234567890`. |
| `sample_837p.edi` | X12 EDI 837P (professional) claim file for the Phase 10 intake parser. Same member/provider as the bundle. |

> CPT/procedure codes are **not** included — they are AMA-licensed and must be entered by an
> ADMIN at runtime (Admin → Procedure Codes). Until matching procedure codes and fee-schedule
> rates exist, sample claims will deny at `CoverageRule` / flag `NO_RATE`. See
> [docs/getting-started.md → First-Run Admin Setup](../docs/getting-started.md#first-run-admin-setup).

## Load the seed dataset

```bash
psql -U meridian -d meridian_claims -f samples/sample_data.sql
```

## Try electronic intake

Configure the inbound directory and enable the poller (see
[docs/configuration.md → Batch claim intake](../docs/configuration.md)), then drop a file in:

```bash
cp samples/sample_intake_bundle.json /path/to/configured/inbound/   # FHIR JSON  (Phase 9)
cp samples/sample_837p.edi          /path/to/configured/inbound/    # X12 837P   (Phase 10)
```

The poller picks the parser by extension (`.json` → FHIR, `.edi`/`.x12`/`.837` → X12),
adjudicates clean claims, quarantines bad records, and moves the file to `archive/` or
`rejected/`. Monitor results at **Admin → Intake Batches**.
