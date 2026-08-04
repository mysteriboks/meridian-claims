package com.meridian.claims.service;

import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.intake.EligibilityResponse;
import com.meridian.claims.intake.X12Edi271Parser;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for MockEligibilityClient (Phase 16 dev-safe default). */
public class MockEligibilityClientTest {

    private MockEligibilityClient client;
    private MemberCoverageDAO memberCoverageDAO;
    private X12Edi271Parser parser;
    private Member member;

    @Before
    public void setUp() {
        memberCoverageDAO = mock(MemberCoverageDAO.class);
        client = new MockEligibilityClient();
        ReflectionTestUtils.setField(client, "memberCoverageDAO", memberCoverageDAO);
        parser = new X12Edi271Parser();

        member = new Member();
        member.setId(20);
        member.setFirstName("JANE");
        member.setLastName("SMITH");
        member.setMemberNumber("M200020");
    }

    @Test
    public void memberWithActiveCoverage_returnsParseable271WithEb01One() throws Exception {
        MemberCoverage active = new MemberCoverage();
        active.setEffectiveDate(daysFromNow(-30));
        active.setTerminationDate(null);
        active.setPlanName("Gold PPO");
        when(memberCoverageDAO.findActiveByMemberId(20)).thenReturn(Arrays.asList(active));

        String edi271 = client.checkEligibility(member, null, "30", "270-EDI");

        List<EligibilityResponse> parsed = parser.parse(edi271);
        assertEquals(1, parsed.size());
        assertEquals("1", parsed.get(0).getEb01Code());
        assertEquals("Gold PPO", parsed.get(0).getPlanDescription());
        assertEquals("M200020", parsed.get(0).getMemberNumber());
    }

    @Test
    public void memberWithNoActiveCoverage_returnsParseable271WithEb01Six() throws Exception {
        when(memberCoverageDAO.findActiveByMemberId(20)).thenReturn(new ArrayList<MemberCoverage>());

        String edi271 = client.checkEligibility(member, null, "30", "270-EDI");

        List<EligibilityResponse> parsed = parser.parse(edi271);
        assertEquals("6", parsed.get(0).getEb01Code());
    }

    @Test
    public void memberWithFutureDatedCoverageOnly_treatsAsInactive() throws Exception {
        MemberCoverage future = new MemberCoverage();
        future.setEffectiveDate(daysFromNow(30));
        future.setPlanName("Future Plan");
        when(memberCoverageDAO.findActiveByMemberId(20)).thenReturn(Arrays.asList(future));

        String edi271 = client.checkEligibility(member, null, "30", "270-EDI");

        List<EligibilityResponse> parsed = parser.parse(edi271);
        assertEquals("6", parsed.get(0).getEb01Code());
    }

    private Date daysFromNow(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
}
