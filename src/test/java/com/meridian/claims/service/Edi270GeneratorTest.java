package com.meridian.claims.service;

import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Unit tests for Edi270Generator (Phase 16 — outbound eligibility inquiry request). */
public class Edi270GeneratorTest {

    private Edi270Generator generator;
    private Member member;
    private Provider provider;

    @Before
    public void setUp() {
        generator = new Edi270Generator();

        member = new Member();
        member.setId(42);
        member.setFirstName("JOHN");
        member.setLastName("DOE");
        member.setMemberNumber("M100042");

        provider = new Provider();
        provider.setId(7);
        provider.setName("ACME CLINIC");
        provider.setNpi("1234567893");
    }

    @Test
    public void generate270_includesMemberAndServiceType() {
        String edi = generator.generate270(member, provider, "30");

        assertNotNull(edi);
        assertTrue("ST must announce the 270 transaction set", edi.contains("ST*270*0001"));
        assertTrue("Subscriber NM1 must carry the member number", edi.contains("MI*M100042"));
        assertTrue("Subscriber NM1 must carry the member's name", edi.contains("NM1*IL*1*DOE*JOHN"));
        assertTrue("EQ must carry the requested service type code", edi.contains("EQ*30"));
    }

    @Test
    public void generate270_withProvider_includesProviderNm1() {
        String edi = generator.generate270(member, provider, "30");

        assertTrue("Provider NM1 must carry the NPI", edi.contains("NM1*1P*2*ACME CLINIC*****XX*1234567893"));
    }

    @Test
    public void generate270_withoutProvider_omitsProviderNm1() {
        String edi = generator.generate270(member, null, "30");

        assertTrue("No provider means no 1P loop", !edi.contains("NM1*1P"));
    }

    @Test
    public void generate270_blankServiceType_defaultsToGeneralCoverage() {
        String edi = generator.generate270(member, null, null);

        assertTrue("Blank service type must default to 30 (general coverage)", edi.contains("EQ*30"));
    }

    @Test
    public void generate270_roundTrip_parsesWithStaEDI() throws Exception {
        String edi = generator.generate270(member, provider, "30");

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
