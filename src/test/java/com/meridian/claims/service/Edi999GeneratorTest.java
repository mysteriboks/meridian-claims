package com.meridian.claims.service;

import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for Edi999Generator (Phase 12 — EDI acknowledgments).
 *
 * No dependencies to mock — the generator only needs its @Value config
 * fields, which default to null/false (no disk writes) when constructed
 * directly, matching Edi835GeneratorTest's approach for the parts that
 * don't need reflection injection.
 */
public class Edi999GeneratorTest {

    private Edi999Generator generator;

    @Before
    public void setUp() {
        generator = new Edi999Generator();
    }

    @Test
    public void generate999_allAccepted_ak9GroupCodeA() {
        List<Edi999Generator.TransactionAckStatus> transactions = new ArrayList<Edi999Generator.TransactionAckStatus>();
        transactions.add(new Edi999Generator.TransactionAckStatus("0001", true));
        transactions.add(new Edi999Generator.TransactionAckStatus("0002", true));

        String edi = generator.generate999("ISA-123", "1", transactions);

        assertNotNull(edi);
        assertTrue("Must reference GS06=1 in AK1", edi.contains("AK1*HC*1"));
        assertTrue("Must contain AK2 for transaction 0001", edi.contains("AK2*837*0001"));
        assertTrue("Must contain AK2 for transaction 0002", edi.contains("AK2*837*0002"));
        assertTrue("Both transactions accepted (AK5*A)", countOccurrences(edi, "AK5*A") == 2);
        assertTrue("AK9 group code must be A (all accepted)", edi.contains("AK9*A*2*2*2"));
    }

    @Test
    public void generate999_mixedOutcomes_ak9GroupCodePartial() {
        List<Edi999Generator.TransactionAckStatus> transactions = new ArrayList<Edi999Generator.TransactionAckStatus>();
        transactions.add(new Edi999Generator.TransactionAckStatus("0001", true));
        transactions.add(new Edi999Generator.TransactionAckStatus("0002", false));

        String edi = generator.generate999("ISA-123", "1", transactions);

        assertTrue("Must contain one accept", edi.contains("AK2*837*0001"));
        assertTrue("Must contain one reject", edi.contains("AK2*837*0002"));
        assertTrue("AK9 group code must be P (partial)", edi.contains("AK9*P*2*2*1"));
    }

    @Test
    public void generate999_allRejected_ak9GroupCodeR() {
        List<Edi999Generator.TransactionAckStatus> transactions = new ArrayList<Edi999Generator.TransactionAckStatus>();
        transactions.add(new Edi999Generator.TransactionAckStatus("0001", false));

        String edi = generator.generate999("ISA-123", "1", transactions);

        assertTrue("AK9 group code must be R (all rejected)", edi.contains("AK9*R*1*1*0"));
    }

    @Test
    public void generate999_emptyTransactionList_stillValid() {
        String edi = generator.generate999("ISA-123", "1", new ArrayList<Edi999Generator.TransactionAckStatus>());

        assertNotNull(edi);
        assertTrue("Empty file has nothing to reject → AK9*A", edi.contains("AK9*A*0*0*0"));
    }

    @Test
    public void generate999_roundTrip_parsesWithStaEDI() throws Exception {
        List<Edi999Generator.TransactionAckStatus> transactions = new ArrayList<Edi999Generator.TransactionAckStatus>();
        transactions.add(new Edi999Generator.TransactionAckStatus("0001", true));

        String edi = generator.generate999("ISA-123", "1", transactions);

        assertReachesEndInterchange(edi);
    }

    @Test
    public void generateTa1_accepted_echoesControlNumber() {
        String ta1 = generator.generateTa1("000000001", true, null);

        assertNotNull(ta1);
        assertTrue("TA1 must echo the interchange control number", ta1.contains("TA1*000000001"));
        assertTrue("TA104 must be A", ta1.contains("*A*"));
        assertTrue("No GS/ST wrapper for TA1", !ta1.contains("GS*"));
    }

    @Test
    public void generateTa1_rejected_missingControlNumber_usesUnknownFallback() {
        String ta1 = generator.generateTa1(null, false, "021");

        assertNotNull(ta1);
        assertTrue("Missing control number falls back to X12's all-zeros convention",
            ta1.contains("TA1*000000000"));
        assertTrue("TA104 must be R", ta1.contains("*R*"));
        assertTrue("Custom note code must be honoured", ta1.contains("021"));
    }

    @Test
    public void generateTa1_roundTrip_parsesWithStaEDI() throws Exception {
        String ta1 = generator.generateTa1("000000001", false, "029");
        assertReachesEndInterchange(ta1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private void assertReachesEndInterchange(String edi) throws Exception {
        EDIInputFactory readerFactory = EDIInputFactory.newFactory();
        ByteArrayInputStream bais = new ByteArrayInputStream(edi.getBytes("UTF-8"));
        EDIStreamReader reader = readerFactory.createEDIStreamReader(bais);

        EDIStreamEvent lastEvent = null;
        try {
            while (reader.hasNext()) {
                lastEvent = reader.next();
            }
        } finally {
            reader.close();
        }

        assertTrue("Parser must reach END_INTERCHANGE", EDIStreamEvent.END_INTERCHANGE == lastEvent);
    }
}
