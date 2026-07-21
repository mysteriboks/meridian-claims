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
 */
public class ClaimFileParseResult {

    private final List<SubmitClaimRequest> claims = new ArrayList<SubmitClaimRequest>();
    private final List<RecordError> recordErrors = new ArrayList<RecordError>();

    public void addClaim(SubmitClaimRequest claim) {
        claims.add(claim);
    }

    public void addRecordError(int recordIndex, String reason) {
        recordErrors.add(new RecordError(recordIndex, reason));
    }

    public List<SubmitClaimRequest> getClaims() {
        return claims;
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

    /** One per-record parse failure: the record's 1-based position and the reason. */
    public static class RecordError {
        private final int recordIndex;
        private final String reason;

        public RecordError(int recordIndex, String reason) {
            this.recordIndex = recordIndex;
            this.reason = reason;
        }

        public int getRecordIndex() { return recordIndex; }
        public String getReason() { return reason; }
    }
}
