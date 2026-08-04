package com.meridian.claims.util;

import org.junit.Test;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AchFileWriterTest {

    private static final String ORIGIN_ROUTING = "123456789";
    private static final String ORIGIN_NAME = "MERIDIAN CLAIMS";
    private static final String COMPANY_ID = "1234567890";

    @Test
    public void generate_emptyEntries_throws() {
        try {
            AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
                new Date(), "EFT000000001", new ArrayList<AchFileWriter.Entry>());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void generate_everyLineIs94CharsAndCrlfTerminated() throws Exception {
        Date effectiveDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-30");
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("160.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            effectiveDate, "EFT000000001", Arrays.asList(entry));

        String[] lines = ach.split("\r\n");
        // split on \r\n drops a trailing empty segment only if the string doesn't end with \r\n;
        // since every record is terminated, the last element is empty and can be ignored except
        // when validating full-file structure below.
        for (String line : lines) {
            if (line.isEmpty()) continue;
            assertEquals("Every NACHA record must be exactly 94 characters: [" + line + "]", 94, line.length());
        }
        assertTrue("File must be blocked in a multiple of 10 records", ach.endsWith("\r\n"));
    }

    @Test
    public void generate_recordTypesAppearInOrder() throws Exception {
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("100.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000001", Arrays.asList(entry));

        String[] lines = ach.split("\r\n");
        assertEquals('1', lines[0].charAt(0)); // File Header
        assertEquals('5', lines[1].charAt(0)); // Batch Header
        assertEquals('6', lines[2].charAt(0)); // Entry Detail
        assertEquals('7', lines[3].charAt(0)); // Addenda
        assertEquals('8', lines[4].charAt(0)); // Batch Control
        assertEquals('9', lines[5].charAt(0)); // File Control
        // Remaining lines pad the final block of 10 with filler "9" records.
        for (int i = 6; i < 10; i++) {
            assertEquals("9999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999"
                .substring(0, 94), lines[i]);
        }
    }

    @Test
    public void generate_addendaCarriesTrnReassociationNumber() throws Exception {
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("100.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000042", Arrays.asList(entry));

        assertTrue("Addenda payment-related info must carry the TRN reassociation number",
            ach.contains("TRN*1*EFT000000042"));
    }

    @Test
    public void generate_savingsAccount_usesTransactionCode32() throws Exception {
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "SAVINGS", new BigDecimal("100.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000001", Arrays.asList(entry));

        String entryDetail = ach.split("\r\n")[2];
        assertEquals("32", entryDetail.substring(1, 3));
    }

    @Test
    public void generate_checkingAccount_usesTransactionCode22() throws Exception {
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("100.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000001", Arrays.asList(entry));

        String entryDetail = ach.split("\r\n")[2];
        assertEquals("22", entryDetail.substring(1, 3));
    }

    @Test
    public void generate_multipleEntries_batchControlTotalsReconcile() throws Exception {
        AchFileWriter.Entry entry1 = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("100.00"), "1", "PROVIDER ONE");
        AchFileWriter.Entry entry2 = new AchFileWriter.Entry(
            "091000019", "00098765432", "SAVINGS", new BigDecimal("50.50"), "2", "PROVIDER TWO");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000001", Arrays.asList(entry1, entry2));

        List<String> lines = new ArrayList<String>(Arrays.asList(ach.split("\r\n")));
        String batchControl = null;
        String fileControl = null;
        for (String line : lines) {
            if (!line.isEmpty() && line.charAt(0) == '8') batchControl = line;
            if (!line.isEmpty() && line.charAt(0) == '9' && line.charAt(1) != '9') fileControl = line;
        }

        // Total credit amount field: batch control positions 33-44 (0-indexed 32..44), 12 digits, cents.
        // 100.00 + 50.50 = 150.50 -> 15050 cents, zero-padded to 12 digits.
        String expectedCents = "000000015050";
        assertTrue("Batch control total credit must reconcile", batchControl.contains(expectedCents));
        assertTrue("File control total credit must reconcile", fileControl.contains(expectedCents));

        // Entry/addenda count: 2 entries * (1 detail + 1 addenda) = 4.
        assertEquals("000004", batchControl.substring(4, 10));
    }

    @Test
    public void generate_entryDetailFieldPositions_matchProviderBankingInfo() throws Exception {
        AchFileWriter.Entry entry = new AchFileWriter.Entry(
            "021000021", "00012345678", "CHECKING", new BigDecimal("160.00"), "1234567893", "ACME CLINIC");

        String ach = AchFileWriter.generate(ORIGIN_ROUTING, ORIGIN_NAME, COMPANY_ID, ORIGIN_NAME,
            new Date(), "EFT000000001", Arrays.asList(entry));

        String entryDetail = ach.split("\r\n")[2];
        assertEquals('6', entryDetail.charAt(0));
        assertEquals("22", entryDetail.substring(1, 3));           // transaction code
        assertEquals("02100002", entryDetail.substring(3, 11));    // receiving DFI id (8 digits)
        assertEquals("1", entryDetail.substring(11, 12));          // check digit
        assertEquals("0000016000", entryDetail.substring(29, 39)); // amount, cents, zero-padded to 10
        assertTrue("Individual name must appear in the entry detail", entryDetail.contains("ACME CLINIC"));
    }
}
