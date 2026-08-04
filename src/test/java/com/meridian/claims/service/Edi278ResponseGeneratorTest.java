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

/** Unit tests for Edi278ResponseGenerator (Phase 15 — prior authorization response). */
public class Edi278ResponseGeneratorTest {

    private Edi278ResponseGenerator generator;

    @Before
    public void setUp() {
        generator = new Edi278ResponseGenerator();
    }

    @Test
    public void generate278Response_approved_includesRefAndCertifiedUm() {
        List<Edi278ResponseGenerator.PriorAuthResult> results = new ArrayList<Edi278ResponseGenerator.PriorAuthResult>();
        results.add(Edi278ResponseGenerator.PriorAuthResult.approved("0001", "PA-ABCD1234"));

        String edi = generator.generate278Response("ISA-999", results);

        assertNotNull(edi);
        assertTrue("Must correlate via TRN with the ST02 control number", edi.contains("TRN*2*0001"));
        assertTrue("Approved requests report the A1 certification code", edi.contains("UM*A1"));
        assertTrue("Approved requests carry our own auth number via REF*1K", edi.contains("REF*1K*PA-ABCD1234"));
        assertTrue("BHT must carry the ISA control number for correlation", edi.contains("BHT*0007*11*ISA-999"));
    }

    @Test
    public void generate278Response_denied_includesReasonAndNotCertifiedUm() {
        List<Edi278ResponseGenerator.PriorAuthResult> results = new ArrayList<Edi278ResponseGenerator.PriorAuthResult>();
        results.add(Edi278ResponseGenerator.PriorAuthResult.denied("0002", "invalid procedure code"));

        String edi = generator.generate278Response("ISA-999", results);

        assertTrue("Denied requests report the A3 certification code", edi.contains("UM*A3"));
        assertTrue("Denial reason surfaces via NTE", edi.contains("invalid procedure code"));
        assertTrue("Denied requests have no auth number ref", !edi.contains("REF*1K"));
    }

    @Test
    public void generate278Response_emptyResultList_stillValid() {
        String edi = generator.generate278Response("ISA-999", new ArrayList<Edi278ResponseGenerator.PriorAuthResult>());

        assertNotNull(edi);
        assertTrue(edi.contains("BHT"));
        assertTrue(edi.contains("IEA"));
    }

    @Test
    public void generate278Response_roundTrip_parsesWithStaEDI() throws Exception {
        List<Edi278ResponseGenerator.PriorAuthResult> results = new ArrayList<Edi278ResponseGenerator.PriorAuthResult>();
        results.add(Edi278ResponseGenerator.PriorAuthResult.approved("0001", "PA-ABCD1234"));
        results.add(Edi278ResponseGenerator.PriorAuthResult.denied("0002", "provider not found"));

        String edi = generator.generate278Response("ISA-999", results);

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
