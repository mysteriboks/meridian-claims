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

/** Unit tests for Edi277CaGenerator (Phase 12 — EDI acknowledgments). */
public class Edi277CaGeneratorTest {

    private Edi277CaGenerator generator;

    @Before
    public void setUp() {
        generator = new Edi277CaGenerator();
    }

    @Test
    public void generate277Ca_acceptedClaim_includesRefAndAcceptStc() {
        List<Edi277CaGenerator.ClaimAckStatus> claims = new ArrayList<Edi277CaGenerator.ClaimAckStatus>();
        claims.add(Edi277CaGenerator.ClaimAckStatus.accepted("0001", "CLM-000042"));

        String edi = generator.generate277Ca("ISA-999", claims);

        assertNotNull(edi);
        assertTrue("Must correlate via TRN with the ST02 control number", edi.contains("TRN*2*0001"));
        assertTrue("Accepted claims report the A2 category status", edi.contains("STC*A2:19"));
        assertTrue("Accepted claims carry our own claim number via REF*1K", edi.contains("REF*1K*CLM-000042"));
        assertTrue("BHT must carry the ISA control number for correlation", edi.contains("BHT*0010*08*ISA-999"));
    }

    @Test
    public void generate277Ca_rejectedClaim_includesReasonAndRejectStc() {
        List<Edi277CaGenerator.ClaimAckStatus> claims = new ArrayList<Edi277CaGenerator.ClaimAckStatus>();
        claims.add(Edi277CaGenerator.ClaimAckStatus.rejected("0002", "member number not found"));

        String edi = generator.generate277Ca("ISA-999", claims);

        assertTrue("Rejected claims report the A3 category status", edi.contains("STC*A3:21"));
        assertTrue("Rejection reason surfaces via NTE", edi.contains("member number not found"));
        assertTrue("Rejected claims have no payer claim control ref", !edi.contains("REF*1K"));
    }

    @Test
    public void generate277Ca_emptyClaimList_stillValid() {
        String edi = generator.generate277Ca("ISA-999", new ArrayList<Edi277CaGenerator.ClaimAckStatus>());

        assertNotNull(edi);
        assertTrue(edi.contains("BHT"));
        assertTrue(edi.contains("IEA"));
    }

    @Test
    public void generate277Ca_roundTrip_parsesWithStaEDI() throws Exception {
        List<Edi277CaGenerator.ClaimAckStatus> claims = new ArrayList<Edi277CaGenerator.ClaimAckStatus>();
        claims.add(Edi277CaGenerator.ClaimAckStatus.accepted("0001", "CLM-000042"));
        claims.add(Edi277CaGenerator.ClaimAckStatus.rejected("0002", "bad CPT code"));

        String edi = generator.generate277Ca("ISA-999", claims);

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
