package com.meridian.claims.service;

import com.meridian.claims.model.ClaimStatus;
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

/** Unit tests for Edi277Generator (Phase 14 — claim status response). */
public class Edi277GeneratorTest {

    private Edi277Generator generator;

    @Before
    public void setUp() {
        generator = new Edi277Generator();
    }

    @Test
    public void generate277_foundClaim_includesRefAndStatusStc() {
        List<Edi277Generator.ClaimStatusResult> results = new ArrayList<Edi277Generator.ClaimStatusResult>();
        results.add(Edi277Generator.ClaimStatusResult.found("0001", "CLM-000042", ClaimStatus.PAID));

        String edi = generator.generate277("ISA-999", results);

        assertNotNull(edi);
        assertTrue("Must correlate via TRN with the ST02 control number", edi.contains("TRN*2*0001"));
        assertTrue("PAID maps to F1:2", edi.contains("STC*F1:2"));
        assertTrue("Found claims carry our own claim number via REF*1K", edi.contains("REF*1K*CLM-000042"));
        assertTrue("BHT must carry the ISA control number for correlation", edi.contains("BHT*0010*08*ISA-999"));
    }

    @Test
    public void generate277_claimNotFound_includesNotFoundStc() {
        List<Edi277Generator.ClaimStatusResult> results = new ArrayList<Edi277Generator.ClaimStatusResult>();
        results.add(Edi277Generator.ClaimStatusResult.notFound("0002"));

        String edi = generator.generate277("ISA-999", results);

        assertTrue("Not-found claims report the A4 category status", edi.contains("STC*A4:1"));
        assertTrue("Not-found claims have no payer claim control ref", !edi.contains("REF*1K"));
    }

    @Test
    public void generate277_emptyResultList_stillValid() {
        String edi = generator.generate277("ISA-999", new ArrayList<Edi277Generator.ClaimStatusResult>());

        assertNotNull(edi);
        assertTrue(edi.contains("BHT"));
        assertTrue(edi.contains("IEA"));
    }

    @Test
    public void generate277_roundTrip_parsesWithStaEDI() throws Exception {
        List<Edi277Generator.ClaimStatusResult> results = new ArrayList<Edi277Generator.ClaimStatusResult>();
        results.add(Edi277Generator.ClaimStatusResult.found("0001", "CLM-000042", ClaimStatus.APPROVED));
        results.add(Edi277Generator.ClaimStatusResult.notFound("0002"));

        String edi = generator.generate277("ISA-999", results);

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

    // -------------------------------------------------------------------------
    // STC mapping table — every ClaimStatus must have a distinct, defined code
    // -------------------------------------------------------------------------

    @Test
    public void stcFor_everyClaimStatus_mapsToADefinedCode() {
        for (ClaimStatus status : ClaimStatus.values()) {
            String stc = Edi277Generator.stcFor(status);
            assertNotNull("status " + status + " must map to an STC code", stc);
            assertTrue("STC for " + status + " must be a category:status pair", stc.contains(":"));
        }
    }

    @Test
    public void stcFor_terminalStatuses_mapToFinalizedCategories() {
        assertTrue(Edi277Generator.stcFor(ClaimStatus.PAID).startsWith("F1"));
        assertTrue(Edi277Generator.stcFor(ClaimStatus.DENIED).startsWith("F2"));
        assertTrue(Edi277Generator.stcFor(ClaimStatus.VOIDED).startsWith("F3"));
        assertTrue(Edi277Generator.stcFor(ClaimStatus.REPLACED).startsWith("F3"));
    }

    @Test
    public void stcFor_inProgressStatuses_mapToAcknowledgementCategories() {
        assertTrue(Edi277Generator.stcFor(ClaimStatus.SUBMITTED).startsWith("A1"));
        assertTrue(Edi277Generator.stcFor(ClaimStatus.IN_REVIEW).startsWith("A6"));
        assertTrue(Edi277Generator.stcFor(ClaimStatus.PENDING_INFO).startsWith("A6"));
    }
}
