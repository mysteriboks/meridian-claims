package com.meridian.claims.intake;

/**
 * Thrown by a ClaimFileParser when a file or individual record cannot be
 * parsed into a valid SubmitClaimRequest.
 *
 * For file-level failures (e.g. not valid JSON) the message describes the
 * structural problem. For per-record failures the message identifies the
 * record position and the specific validation error; callers quarantine the
 * record and continue processing the rest of the file.
 */
public class IntakeParseException extends Exception {

    public IntakeParseException(String message) {
        super(message);
    }

    public IntakeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
