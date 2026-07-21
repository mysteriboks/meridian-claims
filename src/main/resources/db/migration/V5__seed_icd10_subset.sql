-- Phase 3: representative ICD-10-CM seed (public domain, CMS).
--
-- This is a curated subset of common diagnosis codes so the system is usable
-- out of the box. ICD-10-CM is public domain, so seeding it is permitted
-- (unlike CPT, which is AMA-licensed and stays admin-entered only).
--
-- The full ~70k CMS code set is NOT shipped in the MVP; the annual full-set
-- load remains a future append-only migration. Admins may add/edit any code
-- via the Lookup Administration screens regardless of this seed.
--
-- Idempotent: each INSERT is guarded by NOT EXISTS so re-running (or running
-- after an admin has edited a code) never overwrites admin changes.

INSERT INTO diagnosis_codes (code, description, active)
SELECT v.code, v.description, TRUE
FROM (VALUES
    ('E11.9',  'Type 2 diabetes mellitus without complications'),
    ('E11.65', 'Type 2 diabetes mellitus with hyperglycemia'),
    ('E78.5',  'Hyperlipidemia, unspecified'),
    ('I10',    'Essential (primary) hypertension'),
    ('I25.10', 'Atherosclerotic heart disease of native coronary artery without angina pectoris'),
    ('I48.91', 'Unspecified atrial fibrillation'),
    ('I50.9',  'Heart failure, unspecified'),
    ('J02.9',  'Acute pharyngitis, unspecified'),
    ('J06.9',  'Acute upper respiratory infection, unspecified'),
    ('J18.9',  'Pneumonia, unspecified organism'),
    ('J20.9',  'Acute bronchitis, unspecified'),
    ('J44.9',  'Chronic obstructive pulmonary disease, unspecified'),
    ('J45.909','Unspecified asthma, uncomplicated'),
    ('K21.9',  'Gastro-esophageal reflux disease without esophagitis'),
    ('K29.70', 'Gastritis, unspecified, without bleeding'),
    ('K59.00', 'Constipation, unspecified'),
    ('M25.561','Pain in right knee'),
    ('M25.562','Pain in left knee'),
    ('M54.5',  'Low back pain'),
    ('M54.2',  'Cervicalgia'),
    ('M79.601','Pain in right arm'),
    ('N39.0',  'Urinary tract infection, site not specified'),
    ('R05.9',  'Cough, unspecified'),
    ('R07.9',  'Chest pain, unspecified'),
    ('R10.9',  'Unspecified abdominal pain'),
    ('R51.9',  'Headache, unspecified'),
    ('R53.83', 'Other fatigue'),
    ('R42',    'Dizziness and giddiness'),
    ('Z00.00', 'Encounter for general adult medical examination without abnormal findings'),
    ('Z00.121','Encounter for routine child health examination with abnormal findings'),
    ('Z23',    'Encounter for immunization'),
    ('F32.9',  'Major depressive disorder, single episode, unspecified'),
    ('F41.1',  'Generalized anxiety disorder'),
    ('F41.9',  'Anxiety disorder, unspecified'),
    ('G43.909','Migraine, unspecified, not intractable, without status migrainosus'),
    ('G47.33', 'Obstructive sleep apnea'),
    ('E66.9',  'Obesity, unspecified'),
    ('E03.9',  'Hypothyroidism, unspecified'),
    ('D64.9',  'Anemia, unspecified'),
    ('B34.9',  'Viral infection, unspecified')
) AS v(code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_codes d WHERE d.code = v.code
);
