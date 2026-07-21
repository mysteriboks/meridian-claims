package com.meridian.claims.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ValidationUtilTest {

    // --- required ---

    @Test public void required_null_isFalse()  { assertFalse(ValidationUtil.required(null)); }
    @Test public void required_blank_isFalse() { assertFalse(ValidationUtil.required("  ")); }
    @Test public void required_value_isTrue()  { assertTrue(ValidationUtil.required("x")); }

    // --- NPI ---

    @Test public void npi_tenDigits_valid()    { assertTrue(ValidationUtil.npi("1234567890")); }
    @Test public void npi_nineDigits_invalid() { assertFalse(ValidationUtil.npi("123456789")); }
    @Test public void npi_letters_invalid()    { assertFalse(ValidationUtil.npi("123456789A")); }
    @Test public void npi_null_invalid()       { assertFalse(ValidationUtil.npi(null)); }

    @Test public void validateNpi_missing_returnsError() {
        assertNotNull(ValidationUtil.validateNpi(""));
    }
    @Test public void validateNpi_valid_returnsNull() {
        assertNull(ValidationUtil.validateNpi("1234567890"));
    }

    // --- ICD-10 ---

    @Test public void icd10_simpleCode_valid()     { assertTrue(ValidationUtil.icd10Code("A01")); }
    @Test public void icd10_withDecimal_valid()    { assertTrue(ValidationUtil.icd10Code("E11.9")); }
    @Test public void icd10_longCode_valid()       { assertTrue(ValidationUtil.icd10Code("M54.5")); }
    @Test public void icd10_badFormat_invalid()    { assertFalse(ValidationUtil.icd10Code("1AB")); }
    @Test public void icd10_null_invalid()         { assertFalse(ValidationUtil.icd10Code(null)); }

    @Test public void validateDiagnosisCode_valid_returnsNull() {
        assertNull(ValidationUtil.validateDiagnosisCode("E11.9"));
    }
    @Test public void validateDiagnosisCode_blank_returnsError() {
        assertNotNull(ValidationUtil.validateDiagnosisCode(""));
    }
    @Test public void validateDiagnosisCode_badFormat_returnsError() {
        assertNotNull(ValidationUtil.validateDiagnosisCode("XYZ123456789"));
    }

    // --- CPT ---

    @Test public void cpt_fiveDigits_valid()         { assertTrue(ValidationUtil.cptCode("99213")); }
    @Test public void cpt_withModifier_valid()       { assertTrue(ValidationUtil.cptCode("99213-25")); }
    @Test public void cpt_fourDigits_invalid()       { assertFalse(ValidationUtil.cptCode("9921")); }
    @Test public void cpt_null_invalid()             { assertFalse(ValidationUtil.cptCode(null)); }

    @Test public void validateProcedureCode_valid_returnsNull() {
        assertNull(ValidationUtil.validateProcedureCode("99213"));
    }
    @Test public void validateProcedureCode_blank_returnsError() {
        assertNotNull(ValidationUtil.validateProcedureCode(""));
    }

    // --- safeText ---

    @Test public void safeText_normal_valid()          { assertTrue(ValidationUtil.safeText("hello world")); }
    @Test public void safeText_withAngleBracket_invalid() { assertFalse(ValidationUtil.safeText("<script>")); }
    @Test public void safeText_null_valid()            { assertTrue(ValidationUtil.safeText(null)); }

    // --- validateRequiredText / validateOptionalText ---

    @Test public void validateRequiredText_present_returnsNull() {
        assertNull(ValidationUtil.validateRequiredText("John", "First name", 100));
    }
    @Test public void validateRequiredText_blank_returnsError() {
        assertNotNull(ValidationUtil.validateRequiredText("", "First name", 100));
    }
    @Test public void validateRequiredText_tooLong_returnsError() {
        assertNotNull(ValidationUtil.validateRequiredText(repeat("x", 256), "Name", 255));
    }
    @Test public void validateOptionalText_blank_returnsNull() {
        assertNull(ValidationUtil.validateOptionalText("", "Notes", 200));
    }
    @Test public void validateOptionalText_tooLong_returnsError() {
        assertNotNull(ValidationUtil.validateOptionalText(repeat("x", 201), "Notes", 200));
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
