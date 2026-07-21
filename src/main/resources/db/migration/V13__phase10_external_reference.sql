-- Phase 10: store X12 interchange control number for EDI-submitted claims
ALTER TABLE claims ADD COLUMN external_reference VARCHAR(50);
