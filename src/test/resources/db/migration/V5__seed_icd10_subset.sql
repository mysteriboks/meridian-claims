-- H2 test stub mirroring the production ICD-10 subset seed. A handful of codes
-- is enough to prove the seed applies and the diagnosis_codes lookup is
-- populated; the production migration carries the full curated subset.

INSERT INTO diagnosis_codes (code, description, active)
SELECT v.code, v.description, TRUE
FROM (VALUES
    ('E11.9', 'Type 2 diabetes mellitus without complications'),
    ('I10',   'Essential (primary) hypertension'),
    ('M54.5', 'Low back pain'),
    ('J06.9', 'Acute upper respiratory infection, unspecified'),
    ('Z23',   'Encounter for immunization')
) AS v(code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_codes d WHERE d.code = v.code
);
