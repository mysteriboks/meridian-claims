package com.meridian.claims.intake;

import com.meridian.claims.controller.SubmitClaimRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing one inbound claim file.
 *
 * Separates successfully-parsed claims from per-record parse failures so the
 * caller can submit the good records and quarantine the bad ones individually
 * — one malformed record must not abort the whole file (Phase 9 exit criterion).
 *
 * A file that is structurally unparseable (not valid JSON, not a recognised
 * FHIR resource type) is a file-level failure and is signalled by the parser
 * throwing IntakeParseException instead of returning a result.
 *
 * Phase 12 adds optional EDI interchange/transaction control numbers (ISA13,
 * GS06, per-record ST02) so IntakeService can generate accurate TA1/999/277CA
 * acknowledgments for X12 files. These fields are X12-only — FhirClaimFileParser
 * leaves them unset (null), which every consumer treats the same as "not EDI".
 */
public class ClaimFileParseResult {

    private final List<SubmitClaimRequest> claims = new ArrayList<SubmitClaimRequest>();
    private final List<String> claimStControlNumbers = new ArrayList<String>();
    private final List<RecordError> recordErrors = new ArrayList<RecordError>();

    private String isaControlNumber;
    private String gsControlNumber;

    public void addClaim(SubmitClaimRequest claim) {
        addClaim(claim, null);
    }

    /** X12 parsers supply the originating transaction's ST02 control number. */
    public void addClaim(SubmitClaimRequest claim, String stControlNumber) {
        claims.add(claim);
        claimStControlNumbers.add(stControlNumber);
    }

    public void addRecordError(int recordIndex, String reason) {
        addRecordError(recordIndex, reason, null);
    }

    /** X12 parsers supply the originating transaction's ST02 control number. */
    public void addRecordError(int recordIndex, String reason, String stControlNumber) {
        recordErrors.add(new RecordError(recordIndex, reason, stControlNumber));
    }

    public List<SubmitClaimRequest> getClaims() {
        return claims;
    }

    /** ST02 control number for the claim at the given index in {@link #getClaims()}; may be null. */
    public String getClaimStControlNumber(int index) {
        return claimStControlNumbers.get(index);
    }

    public List<RecordError> getRecordErrors() {
        return recordErrors;
    }

    public boolean hasRecordErrors() {
        return !recordErrors.isEmpty();
    }

    /** Total records the file contained (parsed + failed). */
    public int totalRecords() {
        return claims.size() + recordErrors.size();
    }

    /** ISA13 (interchange control number). X12-only; null for FHIR files. */
    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    /** GS06 (functional group control number). X12-only; null for FHIR files. */
    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    /** One per-record parse failure: the record's 1-based position and the reason. */
    public static class RecordError {
        private final int recordIndex;
        private final String reason;
        private final String stControlNumber;

        public RecordError(int recordIndex, String reason) {
            this(recordIndex, reason, null);
        }

        public RecordError(int recordIndex, String reason, String stControlNumber) {
            this.recordIndex = recordIndex;
            this.reason = reason;
            this.stControlNumber = stControlNumber;
        }

        public int getRecordIndex() { return recordIndex; }
        public String getReason() { return reason; }
        public String getStControlNumber() { return stControlNumber; }
    }
}
