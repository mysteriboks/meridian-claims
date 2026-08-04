package com.meridian.claims.util;

import java.util.regex.Pattern;

/**
 * Server-side input validation helpers.
 *
 * All public methods return true if the value is valid.
 * Callers should use these in controllers before passing data to the service layer.
 */
public final class ValidationUtil {

    // ICD-10-CM: letter + 2 digits, optionally followed by a dot and 1-4 alphanumeric chars.
    // Examples: A01, E11.9, Z23, M54.5
    private static final Pattern ICD10 = Pattern.compile(
            "^[A-TV-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$", Pattern.CASE_INSENSITIVE);

    // CPT: exactly 5 digits, optionally followed by a 2-char modifier (e.g. 99213, 99213-25).
    private static final Pattern CPT = Pattern.compile(
            "^[0-9]{5}(-[0-9A-Z]{2})?$", Pattern.CASE_INSENSITIVE);

    // NPI: exactly 10 digits.
    private static final Pattern NPI = Pattern.compile("^[0-9]{10}$");

    // ACH routing number (ABA number): exactly 9 digits.
    private static final Pattern ACH_ROUTING = Pattern.compile("^[0-9]{9}$");

    // Safe free-text: rejects the angle brackets that could open/close an HTML tag.
    // Apostrophes, ampersands, and quotes are intentionally ALLOWED — legitimate
    // names ("O'Brien", "Smith & Sons") contain them, and output is escaped at the
    // view layer via <c:out>, which is the correct place to neutralise XSS.
    private static final Pattern SAFE_TEXT = Pattern.compile(
            "^[^<>]{0,2000}$");

    private ValidationUtil() {
        // utility class
    }

    /** True if s is non-null and non-blank. */
    public static boolean required(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** True if s is non-null, non-blank, and at most maxLen characters. */
    public static boolean maxLength(String s, int maxLen) {
        return s != null && s.length() <= maxLen;
    }

    /** True if s is a well-formed ICD-10-CM code. */
    public static boolean icd10Code(String s) {
        return s != null && ICD10.matcher(s.trim()).matches();
    }

    /** True if s is a well-formed CPT procedure code. */
    public static boolean cptCode(String s) {
        return s != null && CPT.matcher(s.trim()).matches();
    }

    /** True if s is a 10-digit NPI. */
    public static boolean npi(String s) {
        return s != null && NPI.matcher(s.trim()).matches();
    }

    /** True if s is a 9-digit ACH routing (ABA) number. */
    public static boolean achRoutingNumber(String s) {
        return s != null && ACH_ROUTING.matcher(s.trim()).matches();
    }

    /** True if the value represents a positive integer (e.g. a foreign-key ID). */
    public static boolean positiveId(int id) {
        return id > 0;
    }

    /** True if s contains no HTML/script-injection characters and is within length. */
    public static boolean safeText(String s) {
        if (s == null) return true; // null is treated as absent; use required() separately
        return SAFE_TEXT.matcher(s).matches();
    }

    /**
     * Validates a procedure code field: required, CPT format, max 20 chars.
     * Returns an error message or null if valid.
     */
    public static String validateProcedureCode(String code) {
        if (!required(code)) return "Procedure code is required.";
        if (code.length() > 20) return "Procedure code must not exceed 20 characters.";
        if (!cptCode(code)) return "Procedure code must be a valid CPT code (5 digits, optional 2-char modifier).";
        return null;
    }

    /**
     * Validates a diagnosis code field: required, ICD-10 format, max 10 chars.
     * Returns an error message or null if valid.
     */
    public static String validateDiagnosisCode(String code) {
        if (!required(code)) return "Diagnosis code is required.";
        if (code.length() > 10) return "Diagnosis code must not exceed 10 characters.";
        if (!icd10Code(code)) return "Diagnosis code must be a valid ICD-10 code (e.g. E11.9).";
        return null;
    }

    /**
     * Validates an NPI field: required, 10 digits.
     * Returns an error message or null if valid.
     */
    public static String validateNpi(String npiValue) {
        if (!required(npiValue)) return "NPI is required.";
        if (!npi(npiValue)) return "NPI must be exactly 10 digits.";
        return null;
    }

    /**
     * Validates a required free-text field with a max length.
     * Returns an error message or null if valid.
     */
    public static String validateRequiredText(String value, String fieldName, int maxLen) {
        if (!required(value)) return fieldName + " is required.";
        if (!maxLength(value, maxLen)) return fieldName + " must not exceed " + maxLen + " characters.";
        if (!safeText(value)) return fieldName + " contains invalid characters.";
        return null;
    }

    /**
     * Validates an optional free-text field (blank is allowed) with a max length.
     * Returns an error message or null if valid.
     */
    public static String validateOptionalText(String value, String fieldName, int maxLen) {
        if (value == null || value.trim().isEmpty()) return null; // optional, absent is fine
        if (!maxLength(value, maxLen)) return fieldName + " must not exceed " + maxLen + " characters.";
        if (!safeText(value)) return fieldName + " contains invalid characters.";
        return null;
    }
}
