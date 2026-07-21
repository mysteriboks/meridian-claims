-- =============================================================================
-- Meridian Claims — Sample Data Loader
-- =============================================================================
-- Purpose : Populates a freshly-migrated development database with realistic
--           healthcare claims data so every screen, report, and scheduled job
--           has something to show immediately.
-- Run     : psql -U meridian -d meridian_claims -f sample_data.sql
--           (Must be run AFTER Flyway migrations V1-V11 have been applied.)
-- Notes   :
--   * CPT codes used here are illustrative only.  The AMA license requires
--     that any production CPT entries be entered by an Admin, not seeded.
--   * ICD-10 codes are public-domain; a subset is already seeded by V5.
--   * Passwords are bcrypt hashes of the values shown in the comments.
--   * Adjust plan parameters, dates, and amounts to match your test scenarios.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. USERS  (5 roles — each logs in with the shown password)
-- ---------------------------------------------------------------------------
INSERT INTO users (username, full_name, email, password_hash, role, active, force_password_reset, created_at, updated_at)
VALUES
  ('admin',    'Alex Administrator', 'admin@meridian.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LpTsodXFzXS',  -- password: meridian1
   'ADMIN',    TRUE, FALSE, NOW(), NOW()),
  ('reviewer', 'Rachel Reviewer', 'reviewer@meridian.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LpTsodXFzXS',
   'REVIEWER', TRUE, FALSE, NOW(), NOW()),
  ('staff1',   'Sam Staff', 'staff1@meridian.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LpTsodXFzXS',
   'STAFF',    TRUE, FALSE, NOW(), NOW()),
  ('finance1', 'Fiona Finance', 'finance1@meridian.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LpTsodXFzXS',
   'FINANCE',  TRUE, FALSE, NOW(), NOW()),
  ('analyst1', 'Anna Analyst', 'analyst1@meridian.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LpTsodXFzXS',
   'ANALYST',  TRUE, FALSE, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. PROVIDERS  (mix of in-network and out-of-network)
-- ---------------------------------------------------------------------------
INSERT INTO providers (npi, name, provider_type, network_status, address, phone, created_at, updated_at)
VALUES
  ('1234567890', 'Riverside Primary Care',      'INDIVIDUAL', 'IN_NETWORK',  '100 Oak St, Springfield, IL 62701',   '217-555-0100', NOW(), NOW()),
  ('2345678901', 'Springfield Orthopedic Grp',  'GROUP',      'IN_NETWORK',  '200 Elm Ave, Springfield, IL 62702',  '217-555-0200', NOW(), NOW()),
  ('3456789012', 'Mercy General Hospital',       'FACILITY',   'IN_NETWORK',  '300 Main Blvd, Springfield, IL 62703','217-555-0300', NOW(), NOW()),
  ('4567890123', 'Lakeside Dermatology',         'INDIVIDUAL', 'OUT_OF_NETWORK','400 Pine Rd, Decatur, IL 62521',   '217-555-0400', NOW(), NOW()),
  ('5678901234', 'Central Radiology Partners',   'GROUP',      'IN_NETWORK',  '500 Cedar Ln, Springfield, IL 62704', '217-555-0500', NOW(), NOW())
ON CONFLICT (npi) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. PLANS  (PPO, HMO, EPO)
-- ---------------------------------------------------------------------------
INSERT INTO plans (plan_name, plan_type, deductible_amount, oop_max, copay_amount,
                   coverage_pct_in_network, coverage_pct_out_network,
                   benefit_year_start, timely_filing_days, created_at, updated_at)
VALUES
  ('MedAdvantage PPO Gold',  'PPO', 500.00, 3000.00, 30.00, 80.00, 60.00, '2026-01-01', 180, NOW(), NOW()),
  ('MedAdvantage PPO Silver','PPO', 1000.00,5000.00, 40.00, 70.00, 50.00, '2026-01-01', 180, NOW(), NOW()),
  ('MedSelect HMO',          'HMO', 250.00, 2000.00, 20.00, 90.00, 0.00,  '2026-01-01', 90,  NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Capture IDs for FK use below
DO $$
DECLARE
  p_ppo_gold   INTEGER;
  p_ppo_silver INTEGER;
  p_hmo        INTEGER;
BEGIN
  SELECT id INTO p_ppo_gold   FROM plans WHERE plan_name = 'MedAdvantage PPO Gold';
  SELECT id INTO p_ppo_silver FROM plans WHERE plan_name = 'MedAdvantage PPO Silver';
  SELECT id INTO p_hmo        FROM plans WHERE plan_name = 'MedSelect HMO';

  -- 4. PROCEDURE CODES (CPT — illustrative; enter via Admin for AMA compliance)
  INSERT INTO procedure_codes (code, description, service_type, active, created_at, updated_at)
  VALUES
    ('99213', 'Office visit, established patient, moderate complexity',  'OFFICE_VISIT',  TRUE, NOW(), NOW()),
    ('99214', 'Office visit, established patient, high complexity',      'OFFICE_VISIT',  TRUE, NOW(), NOW()),
    ('99232', 'Subsequent hospital care, moderate complexity',           'INPATIENT',     TRUE, NOW(), NOW()),
    ('73721', 'MRI, any joint of lower extremity',                       'RADIOLOGY',     TRUE, NOW(), NOW()),
    ('27447', 'Arthroplasty, knee, condyle and plateau; medial AND lateral compartments', 'SURGERY', TRUE, NOW(), NOW()),
    ('93000', 'Electrocardiogram, routine ECG',                          'CARDIOLOGY',    TRUE, NOW(), NOW()),
    ('85025', 'Complete blood count (CBC) with differential',            'LAB',           TRUE, NOW(), NOW()),
    ('71046', 'Chest radiograph, 2 views',                               'RADIOLOGY',     TRUE, NOW(), NOW())
  ON CONFLICT (code) DO NOTHING;

  -- 5. PLAN COVERAGE RULES
  INSERT INTO plan_coverage_rules (plan_id, service_type, coverage_pct, requires_prior_auth, requires_referral, active, created_at, updated_at)
  VALUES
    (p_ppo_gold,   'OFFICE_VISIT',  80.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_gold,   'INPATIENT',     80.00, TRUE,  FALSE, TRUE, NOW(), NOW()),
    (p_ppo_gold,   'RADIOLOGY',     80.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_gold,   'SURGERY',       80.00, TRUE,  FALSE, TRUE, NOW(), NOW()),
    (p_ppo_gold,   'CARDIOLOGY',    80.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_gold,   'LAB',           80.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_silver, 'OFFICE_VISIT',  70.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_silver, 'INPATIENT',     70.00, TRUE,  FALSE, TRUE, NOW(), NOW()),
    (p_ppo_silver, 'RADIOLOGY',     70.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_ppo_silver, 'SURGERY',       70.00, TRUE,  FALSE, TRUE, NOW(), NOW()),
    (p_ppo_silver, 'LAB',           70.00, FALSE, FALSE, TRUE, NOW(), NOW()),
    (p_hmo,        'OFFICE_VISIT',  90.00, FALSE, TRUE,  TRUE, NOW(), NOW()),
    (p_hmo,        'INPATIENT',     90.00, TRUE,  TRUE,  TRUE, NOW(), NOW()),
    (p_hmo,        'RADIOLOGY',     90.00, FALSE, TRUE,  TRUE, NOW(), NOW()),
    (p_hmo,        'LAB',           90.00, FALSE, FALSE, TRUE, NOW(), NOW())
  ON CONFLICT DO NOTHING;

  -- 6. FEE SCHEDULE RATES  (plan-wide; no provider-specific override)
  INSERT INTO fee_schedule_rates (plan_id, provider_id, procedure_code, allowed_amount, effective_date, expiration_date, created_at, updated_at)
  VALUES
    (p_ppo_gold,  NULL, '99213',  85.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '99214', 120.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '99232', 210.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '73721', 650.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '27447',4200.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '93000',  55.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '85025',  32.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_gold,  NULL, '71046',  95.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_silver,NULL, '99213',  75.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_silver,NULL, '99214', 105.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_silver,NULL, '73721', 580.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_silver,NULL, '27447',3800.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_ppo_silver,NULL, '85025',  28.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_hmo,       NULL, '99213',  80.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_hmo,       NULL, '99214', 110.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_hmo,       NULL, '93000',  50.00, '2026-01-01', NULL, NOW(), NOW()),
    (p_hmo,       NULL, '85025',  30.00, '2026-01-01', NULL, NOW(), NOW())
  ON CONFLICT DO NOTHING;

END $$;

-- ---------------------------------------------------------------------------
-- 7. MEMBERS  (diverse demographics, single and married)
-- ---------------------------------------------------------------------------
INSERT INTO members (member_number, first_name, last_name, dob, address, phone, email, status, created_at, updated_at)
VALUES
  ('MBR-001', 'James',    'Anderson',  '1968-03-15', '12 Elm St, Springfield, IL 62701',   '217-555-1001', 'j.anderson@example.com',  'ACTIVE', NOW(), NOW()),
  ('MBR-002', 'Patricia', 'Williams',  '1975-07-22', '34 Oak Ave, Springfield, IL 62702',   '217-555-1002', 'p.williams@example.com',  'ACTIVE', NOW(), NOW()),
  ('MBR-003', 'Robert',   'Brown',     '1952-11-08', '56 Maple Dr, Decatur, IL 62521',      '217-555-1003', NULL,                      'ACTIVE', NOW(), NOW()),
  ('MBR-004', 'Linda',    'Jones',     '1980-01-30', '78 Cedar Ln, Springfield, IL 62703',  '217-555-1004', 'l.jones@example.com',     'ACTIVE', NOW(), NOW()),
  ('MBR-005', 'Michael',  'Davis',     '1991-09-14', '90 Pine Rd, Chatham, IL 62629',       '217-555-1005', 'm.davis@example.com',     'ACTIVE', NOW(), NOW()),
  ('MBR-006', 'Barbara',  'Miller',    '1963-05-03', '22 Birch St, Springfield, IL 62704',  '217-555-1006', 'b.miller@example.com',    'ACTIVE', NOW(), NOW()),
  ('MBR-007', 'William',  'Wilson',    '1945-12-19', '44 Walnut Blvd, Rochester, IL 62563', '217-555-1007', NULL,                      'ACTIVE', NOW(), NOW()),
  ('MBR-008', 'Susan',    'Moore',     '1988-06-27', '66 Ash Ct, Springfield, IL 62705',    '217-555-1008', 's.moore@example.com',     'INACTIVE',NOW(), NOW())
ON CONFLICT (member_number) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. MEMBER COVERAGE
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  m1 INTEGER; m2 INTEGER; m3 INTEGER; m4 INTEGER;
  m5 INTEGER; m6 INTEGER; m7 INTEGER;
  ppo_gold INTEGER; ppo_silver INTEGER; hmo INTEGER;
BEGIN
  SELECT id INTO m1 FROM members WHERE member_number = 'MBR-001';
  SELECT id INTO m2 FROM members WHERE member_number = 'MBR-002';
  SELECT id INTO m3 FROM members WHERE member_number = 'MBR-003';
  SELECT id INTO m4 FROM members WHERE member_number = 'MBR-004';
  SELECT id INTO m5 FROM members WHERE member_number = 'MBR-005';
  SELECT id INTO m6 FROM members WHERE member_number = 'MBR-006';
  SELECT id INTO m7 FROM members WHERE member_number = 'MBR-007';
  SELECT id INTO ppo_gold   FROM plans WHERE plan_name = 'MedAdvantage PPO Gold';
  SELECT id INTO ppo_silver FROM plans WHERE plan_name = 'MedAdvantage PPO Silver';
  SELECT id INTO hmo        FROM plans WHERE plan_name = 'MedSelect HMO';

  INSERT INTO member_coverage (member_id, plan_id, coverage_order, effective_date, termination_date, created_at, updated_at)
  VALUES
    (m1, ppo_gold,   'PRIMARY',   '2026-01-01', NULL,         NOW(), NOW()),
    (m2, ppo_gold,   'PRIMARY',   '2026-01-01', NULL,         NOW(), NOW()),
    -- MBR-003 has Medicare primary + PPO secondary (COB scenario)
    (m3, ppo_silver, 'SECONDARY', '2026-01-01', NULL,         NOW(), NOW()),
    (m4, hmo,        'PRIMARY',   '2026-01-01', NULL,         NOW(), NOW()),
    (m5, ppo_silver, 'PRIMARY',   '2026-01-01', NULL,         NOW(), NOW()),
    (m6, ppo_gold,   'PRIMARY',   '2026-01-01', NULL,         NOW(), NOW()),
    (m7, ppo_silver, 'PRIMARY',   '2025-01-01', '2025-12-31', NOW(), NOW())  -- terminated
  ON CONFLICT DO NOTHING;

  -- ---------------------------------------------------------------------------
  -- 9. PRIOR AUTHORIZATIONS  (knee replacement for MBR-001)
  -- ---------------------------------------------------------------------------
  INSERT INTO prior_authorizations (member_id, plan_id, procedure_code, service_type,
    auth_number, authorized_date_from, authorized_date_to, authorized_units, status, created_at, updated_at)
  VALUES
    (m1, ppo_gold, '27447', 'SURGERY', 'PA-2026-0001', '2026-03-01', '2026-06-30', 1, 'APPROVED', NOW(), NOW())
  ON CONFLICT DO NOTHING;

  -- ---------------------------------------------------------------------------
  -- 10. CLAIMS  (a realistic mix of outcomes)
  -- ---------------------------------------------------------------------------

  -- Claim 1: Office visit — auto-approved
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260315-000001', m2,
      (SELECT id FROM providers WHERE npi = '1234567890'), 'ORIGINAL',
      '2026-03-15', '2026-03-20', 'PAID',
      ppo_gold, 'PRIMARY',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '99213', 'Office visit, established patient',
    110.00, 85.00, 52.00, 33.00, 0.00, 30.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260315-000001')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'J06.9', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 2: Denied — untimely filing
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      denial_reason_code, created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20250101-000002', m1,
      (SELECT id FROM providers WHERE npi = '1234567890'), 'ORIGINAL',
      '2025-01-01', '2026-02-01', 'DENIED',      -- 13 months — exceeds 180-day limit
      ppo_gold, 'PRIMARY', 'TIMELY_FILING',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '99213', 'Office visit', 110.00, NULL, NULL, NULL, 0.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20250101-000002')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'I10', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 3: Knee MRI — manual review (over $500 auto-approve threshold)
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      assigned_to_user_id, created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260401-000003', m1,
      (SELECT id FROM providers WHERE npi = '5678901234'), 'ORIGINAL',
      '2026-04-01', '2026-04-05', 'IN_REVIEW',
      ppo_gold, 'PRIMARY',
      (SELECT id FROM users WHERE username = 'reviewer'),
      (SELECT id FROM users WHERE username = 'staff1'),
      NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '73721', 'MRI right knee', 800.00, 650.00, 490.00, 160.00, 160.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260401-000003')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'M23.611', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 4: Knee replacement surgery — approved with prior auth
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      prior_auth_number, created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260410-000004', m1,
      (SELECT id FROM providers WHERE npi = '2345678901'), 'ORIGINAL',
      '2026-04-10', '2026-04-14', 'APPROVED',
      ppo_gold, 'PRIMARY', 'PA-2026-0001',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '27447', 'Total knee arthroplasty, right',
    5200.00, 4200.00, 3340.00, 860.00, 500.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260410-000004')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'M17.11', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 5: Lab work — auto-approved
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260415-000005', m4,
      (SELECT id FROM providers WHERE npi = '1234567890'), 'ORIGINAL',
      '2026-04-15', '2026-04-17', 'PAID',
      hmo, 'PRIMARY',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '85025', 'CBC with differential', 45.00, 30.00, 27.00, 3.00, 0.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260415-000005')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'Z00.00', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 6: COB — secondary coverage (MBR-003)
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      cob_primary_paid, created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260420-000006', m3,
      (SELECT id FROM providers WHERE npi = '1234567890'), 'ORIGINAL',
      '2026-04-20', '2026-04-25', 'IN_REVIEW',
      ppo_silver, 'SECONDARY', 62.00,          -- Medicare paid $62
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '99214', 'Office visit, high complexity',
    145.00, 105.00, 43.00, 0.00, 0.00, 40.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260420-000006')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'E11.9', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 7: Accident claim — paid, triggers subrogation
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      accident_indicator, accident_type, accident_date,
      created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260505-000007', m5,
      (SELECT id FROM providers WHERE npi = '3456789012'), 'ORIGINAL',
      '2026-05-05', '2026-05-08', 'PAID',
      ppo_silver, 'PRIMARY', TRUE, 'AUTO', '2026-05-05',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '99232', 'Hospital care, subsequent', 280.00, 210.00, 147.00, 63.00, 63.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260505-000007')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'S06.0X0A', 1, 'PRIMARY', NOW() FROM c;

  -- Claim 8: PENDING_INFO — info requested
  WITH c AS (
    INSERT INTO claims (claim_number, member_id, provider_id, claim_type,
      date_of_service, submission_date, status, plan_id, coverage_order,
      created_by_user_id, status_entered_at, version, created_at, updated_at)
    VALUES (
      'CLM-20260510-000008', m6,
      (SELECT id FROM providers WHERE npi = '4567890123'), 'ORIGINAL',
      '2026-05-10', '2026-05-12', 'PENDING_INFO',
      ppo_gold, 'PRIMARY',
      (SELECT id FROM users WHERE username = 'staff1'), NOW(), 0, NOW(), NOW()
    ) RETURNING id
  )
  INSERT INTO claim_line_items (claim_id, procedure_code, description, billed_amount,
    allowed_amount, plan_paid_amount, member_responsibility, deductible_applied, copay_applied,
    rate_source, created_at)
  SELECT id, '71046', 'Chest X-ray, 2 views', 120.00, 95.00, 76.00, 19.00, 0.00, 0.00, 'PLAN_WIDE', NOW()
  FROM c;

  WITH c AS (SELECT id FROM claims WHERE claim_number = 'CLM-20260510-000008')
  INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at)
  SELECT id, 'R05.9', 1, 'PRIMARY', NOW() FROM c;

  -- Info request for claim 8
  INSERT INTO info_requests (claim_id, requested_from, requested_by_user_id, due_date, request_notes, status, created_at)
  SELECT
    (SELECT id FROM claims WHERE claim_number = 'CLM-20260510-000008'),
    'PROVIDER',
    (SELECT id FROM users WHERE username = 'reviewer'),
    CURRENT_DATE + 14,
    'Please provide operative notes and prior imaging reports to support medical necessity for chest imaging.',
    'OPEN', NOW();

  -- ---------------------------------------------------------------------------
  -- 11. PAYMENTS  (for approved/paid claims)
  -- ---------------------------------------------------------------------------
  -- Payment for CLM-20260315-000001 (PAID — office visit)
  INSERT INTO payments (claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility,
    partial_payment_flag, status, payment_date, reference_number, created_at, updated_at)
  SELECT
    id, 110.00, 85.00, 52.00, 33.00, FALSE, 'PAID', '2026-03-25', 'ACH-2026-0001', NOW(), NOW()
  FROM claims WHERE claim_number = 'CLM-20260315-000001';

  -- Payment for CLM-20260415-000005 (PAID — lab)
  INSERT INTO payments (claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility,
    partial_payment_flag, status, payment_date, reference_number, created_at, updated_at)
  SELECT
    id, 45.00, 30.00, 27.00, 3.00, FALSE, 'PAID', '2026-04-20', 'ACH-2026-0002', NOW(), NOW()
  FROM claims WHERE claim_number = 'CLM-20260415-000005';

  -- Payment for CLM-20260505-000007 (PAID — accident)
  INSERT INTO payments (claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility,
    partial_payment_flag, status, payment_date, reference_number, created_at, updated_at)
  SELECT
    id, 280.00, 210.00, 147.00, 63.00, FALSE, 'PAID', '2026-05-15', 'ACH-2026-0003', NOW(), NOW()
  FROM claims WHERE claim_number = 'CLM-20260505-000007';

  -- PENDING_PAYMENT for CLM-20260410-000004 (APPROVED — knee replacement)
  INSERT INTO payments (claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility,
    partial_payment_flag, status, created_at, updated_at)
  SELECT
    id, 5200.00, 4200.00, 3340.00, 860.00, FALSE, 'PENDING', NOW(), NOW()
  FROM claims WHERE claim_number = 'CLM-20260410-000004';

  -- ---------------------------------------------------------------------------
  -- 12. EOB DOCUMENTS  (for paid claims)
  -- ---------------------------------------------------------------------------
  INSERT INTO eob_documents (claim_id, member_id, content, delivery_method, generated_at, created_at)
  SELECT
    c.id, c.member_id,
    '<html><body><h2>Explanation of Benefits</h2><p>Claim: CLM-20260315-000001</p><p>Status: PAID</p><p>Plan Paid: $52.00 | Member Responsibility: $33.00</p></body></html>',
    'MAILED', NOW(), NOW()
  FROM claims c WHERE c.claim_number = 'CLM-20260315-000001';

  INSERT INTO eob_documents (claim_id, member_id, content, delivery_method, generated_at, created_at)
  SELECT
    c.id, c.member_id,
    '<html><body><h2>Explanation of Benefits</h2><p>Claim: CLM-20260505-000007</p><p>Status: PAID</p><p>Plan Paid: $147.00 | Member Responsibility: $63.00</p></body></html>',
    'EMAILED', NOW(), NOW()
  FROM claims c WHERE c.claim_number = 'CLM-20260505-000007';

  -- ---------------------------------------------------------------------------
  -- 13. SUBROGATION  (auto-created for accident claim)
  -- ---------------------------------------------------------------------------
  INSERT INTO subrogation_cases (claim_id, opened_date, status, notes, created_at, updated_at)
  SELECT
    id, '2026-05-16', 'OPEN',
    'Auto-opened: patient injured in MVA; third-party insurer (StateAuto) notified 2026-05-16.',
    NOW(), NOW()
  FROM claims WHERE claim_number = 'CLM-20260505-000007'
  ON CONFLICT DO NOTHING;

  -- ---------------------------------------------------------------------------
  -- 14. APPEALS  (for the timely-filing denied claim)
  -- ---------------------------------------------------------------------------
  INSERT INTO appeals (claim_id, member_id, appeal_type, submitted_date, deadline_date,
    status, submitted_by_user_id, created_at, updated_at)
  SELECT
    c.id, c.member_id, 'INTERNAL', '2026-03-01', '2026-03-31',
    'OPEN',
    (SELECT id FROM users WHERE username = 'staff1'),
    NOW(), NOW()
  FROM claims c WHERE c.claim_number = 'CLM-20250101-000002';

  -- ---------------------------------------------------------------------------
  -- 15. SLA BREACHES  (claim 3 has been in IN_REVIEW more than 48 hours)
  -- ---------------------------------------------------------------------------
  INSERT INTO sla_breaches (claim_id, status, expected_by, breached_at)
  SELECT
    id, 'IN_REVIEW', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'
  FROM claims WHERE claim_number = 'CLM-20260401-000003';

  -- ---------------------------------------------------------------------------
  -- 16. CLAIM AUDIT  (key workflow events)
  -- ---------------------------------------------------------------------------
  INSERT INTO claim_audit (claim_id, event_type, old_status, new_status, changed_by_user_id, notes, changed_at)
  SELECT id, 'CLAIM_SUBMITTED', NULL, 'SUBMITTED', (SELECT id FROM users WHERE username='staff1'), NULL, NOW() - INTERVAL '40 days'
  FROM claims WHERE claim_number = 'CLM-20260315-000001'
  UNION ALL
  SELECT id, 'APPROVED', 'SUBMITTED', 'APPROVED', (SELECT id FROM users WHERE username='reviewer'), 'Auto-approved below threshold', NOW() - INTERVAL '39 days'
  FROM claims WHERE claim_number = 'CLM-20260315-000001'
  UNION ALL
  SELECT id, 'CLAIM_SUBMITTED', NULL, 'SUBMITTED', (SELECT id FROM users WHERE username='staff1'), NULL, NOW() - INTERVAL '20 days'
  FROM claims WHERE claim_number = 'CLM-20260401-000003'
  UNION ALL
  SELECT id, 'CLAIM_SUBMITTED', NULL, 'SUBMITTED', (SELECT id FROM users WHERE username='staff1'), NULL, NOW() - INTERVAL '18 days'
  FROM claims WHERE claim_number = 'CLM-20260410-000004'
  UNION ALL
  SELECT id, 'APPROVED', 'IN_REVIEW', 'APPROVED', (SELECT id FROM users WHERE username='reviewer'), 'Prior auth PA-2026-0001 verified', NOW() - INTERVAL '16 days'
  FROM claims WHERE claim_number = 'CLM-20260410-000004';

END $$;

COMMIT;

-- =============================================================================
-- Verification queries (optional — uncomment to check after load)
-- =============================================================================
-- SELECT 'users'              AS tbl, COUNT(*) FROM users;
-- SELECT 'members'            AS tbl, COUNT(*) FROM members;
-- SELECT 'providers'          AS tbl, COUNT(*) FROM providers;
-- SELECT 'plans'              AS tbl, COUNT(*) FROM plans;
-- SELECT 'procedure_codes'    AS tbl, COUNT(*) FROM procedure_codes;
-- SELECT 'plan_coverage_rules'AS tbl, COUNT(*) FROM plan_coverage_rules;
-- SELECT 'fee_schedule_rates' AS tbl, COUNT(*) FROM fee_schedule_rates;
-- SELECT 'claims'             AS tbl, COUNT(*) FROM claims;
-- SELECT 'payments'           AS tbl, COUNT(*) FROM payments;
-- SELECT 'appeals'            AS tbl, COUNT(*) FROM appeals;
-- SELECT 'subrogation_cases'  AS tbl, COUNT(*) FROM subrogation_cases;
-- SELECT 'eob_documents'      AS tbl, COUNT(*) FROM eob_documents;
-- SELECT 'sla_breaches'       AS tbl, COUNT(*) FROM sla_breaches;
