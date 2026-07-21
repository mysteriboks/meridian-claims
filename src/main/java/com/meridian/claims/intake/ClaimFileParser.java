package com.meridian.claims.intake;

/**
 * Seam interface for inbound claim file parsers.
 *
 * Implementations map a file's content to claim requests ready for submission
 * through ClaimService.submit(). Phase 9 ships FhirClaimFileParser (FHIR R4
 * Claim JSON); Phase 10 adds X12Edi837Parser behind the same interface without
 * changing the poller or IntakeService.
 *
 * A parser is responsible for per-record validation (reusing ValidationUtil).
 * Per-record failures are collected in the returned ClaimFileParseResult so the
 * caller can quarantine them individually — one bad record must NOT abort the
 * whole file. Only a structurally unparseable file (bad JSON, unrecognised
 * format) is a file-level failure and throws IntakeParseException.
 */
public interface ClaimFileParser {

    /**
     * Parse the full text content of an inbound file.
     *
     * @param fileContent raw file text (UTF-8)
     * @return a result holding successfully-parsed claims and per-record errors;
     *         never null. An empty file yields a result with no claims and no errors.
     * @throws IntakeParseException only if the file is structurally unparseable
     *         (e.g. not valid JSON / not a recognised FHIR resource type).
     *         Per-record validation failures are reported via the result's
     *         record-error list, not by throwing.
     */
    ClaimFileParseResult parse(String fileContent) throws IntakeParseException;
}
