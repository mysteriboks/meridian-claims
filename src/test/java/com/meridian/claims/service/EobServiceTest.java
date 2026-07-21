package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.EobDocumentDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.EobDocument;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EobServiceTest {

    private EobService eobService;
    private ClaimDAO claimDAO;
    private ClaimLineItemDAO lineItemDAO;
    private MemberDAO memberDAO;
    private ProviderDAO providerDAO;
    private EobDocumentDAO eobDocumentDAO;
    private MailService mailService;

    @Before
    public void setUp() {
        eobService     = new EobService();
        claimDAO       = Mockito.mock(ClaimDAO.class);
        lineItemDAO    = Mockito.mock(ClaimLineItemDAO.class);
        memberDAO      = Mockito.mock(MemberDAO.class);
        providerDAO    = Mockito.mock(ProviderDAO.class);
        eobDocumentDAO = Mockito.mock(EobDocumentDAO.class);
        mailService    = Mockito.mock(MailService.class);

        ReflectionTestUtils.setField(eobService, "claimDAO",       claimDAO);
        ReflectionTestUtils.setField(eobService, "lineItemDAO",    lineItemDAO);
        ReflectionTestUtils.setField(eobService, "memberDAO",      memberDAO);
        ReflectionTestUtils.setField(eobService, "providerDAO",    providerDAO);
        ReflectionTestUtils.setField(eobService, "eobDocumentDAO", eobDocumentDAO);
        ReflectionTestUtils.setField(eobService, "mailService",    mailService);
    }

    @Test
    public void generate_approved_insertsDocAndIncludesLineItems() {
        Claim claim = approvedClaim(1);
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);
        Member member = new Member();
        member.setId(10);
        member.setFirstName("Jane");
        member.setLastName("Doe");
        member.setMemberNumber("M001");
        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Provider provider = new Provider();
        provider.setName("City Clinic");
        provider.setNpi("1234567890");
        Mockito.when(providerDAO.findById(5)).thenReturn(provider);

        ClaimLineItem li = new ClaimLineItem();
        li.setProcedureCode("99213");
        li.setBilledAmount(Money.of("200.00"));
        li.setAllowedAmount(Money.of("180.00"));
        li.setPlanPaidAmount(Money.of("144.00"));
        li.setMemberResponsibility(Money.of("36.00"));
        Mockito.when(lineItemDAO.findByClaimId(1)).thenReturn(Arrays.asList(li));

        EobDocument doc = eobService.generate(1);

        Mockito.verify(eobDocumentDAO).insert(Mockito.any(EobDocument.class));
        assertNotNull(doc);
        assertTrue(doc.getContent().contains("99213"));
        assertTrue(doc.getContent().contains("144.00"));
    }

    @Test
    public void generate_memberHasEmail_sendsEmail() {
        Claim claim = approvedClaim(2);
        Mockito.when(claimDAO.findById(2)).thenReturn(claim);
        Member member = new Member();
        member.setId(10);
        member.setFirstName("John");
        member.setLastName("Smith");
        member.setMemberNumber("M002");
        member.setEmail("john@example.com");
        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());
        Mockito.when(lineItemDAO.findByClaimId(2)).thenReturn(Collections.<ClaimLineItem>emptyList());

        eobService.generate(2);

        Mockito.verify(mailService).send(
            Mockito.eq("john@example.com"),
            Mockito.contains("CLM-TEST-002"),
            Mockito.anyString());
    }

    @Test
    public void generate_memberNoEmail_noEmailSent() {
        Claim claim = approvedClaim(3);
        Mockito.when(claimDAO.findById(3)).thenReturn(claim);
        Member member = new Member();
        member.setId(10);
        member.setFirstName("No");
        member.setLastName("Email");
        member.setMemberNumber("M003");
        member.setEmail(null);
        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());
        Mockito.when(lineItemDAO.findByClaimId(3)).thenReturn(Collections.<ClaimLineItem>emptyList());

        eobService.generate(3);

        Mockito.verify(mailService, Mockito.never()).send(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test(expected = ServiceException.class)
    public void generate_claimNotFound_throws() {
        Mockito.when(claimDAO.findById(99)).thenReturn(null);
        eobService.generate(99);
    }

    @Test
    public void generate_existingEob_returnsExistingWithoutDuplicateInsert() {
        Claim claim = approvedClaim(4);
        Mockito.when(claimDAO.findById(4)).thenReturn(claim);
        EobDocument existing = new EobDocument();
        existing.setId(77);
        existing.setClaimId(4);
        Mockito.when(eobDocumentDAO.findByClaimId(4)).thenReturn(existing);

        EobDocument result = eobService.generate(4);

        Mockito.verify(eobDocumentDAO, Mockito.never()).insert(Mockito.any(EobDocument.class));
        org.junit.Assert.assertEquals(77, result.getId());
    }

    private Claim approvedClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setClaimNumber("CLM-TEST-00" + id);
        c.setMemberId(10);
        c.setProviderId(5);
        c.setStatus(ClaimStatus.APPROVED);
        c.setClaimType(ClaimType.ORIGINAL);
        return c;
    }
}
