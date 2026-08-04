package com.meridian.claims.service;

import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Provider;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for ProviderService.updateBankingInfo (Phase 18 — EFT/ACH disbursement setup). */
public class ProviderServiceTest {

    private ProviderService service;
    private ProviderDAO providerDAO;
    private AuditService auditService;

    @Before
    public void setUp() {
        providerDAO = mock(ProviderDAO.class);
        auditService = mock(AuditService.class);
        service = new ProviderService(providerDAO, auditService);

        Provider provider = new Provider();
        provider.setId(5);
        when(providerDAO.findById(5)).thenReturn(provider);
    }

    @Test
    public void updateBankingInfo_validFields_savesAndAudits() {
        service.updateBankingInfo(5, "021000021", "00012345678", "CHECKING");

        verify(providerDAO).updateBankingInfo(5, "021000021", "00012345678", "CHECKING");
        verify(auditService).record(eq("PROVIDER_BANKING_UPDATED"), anyString(), eq(5L), anyString());
    }

    @Test
    public void updateBankingInfo_allBlank_clearsBankingInfo() {
        service.updateBankingInfo(5, "", "", "");

        verify(providerDAO).updateBankingInfo(5, null, null, null);
    }

    @Test
    public void updateBankingInfo_allNull_clearsBankingInfo() {
        service.updateBankingInfo(5, null, null, null);

        verify(providerDAO).updateBankingInfo(5, null, null, null);
    }

    @Test(expected = ServiceException.class)
    public void updateBankingInfo_routingNumberNotNineDigits_throws() {
        service.updateBankingInfo(5, "12345", "00012345678", "CHECKING");
    }

    @Test(expected = ServiceException.class)
    public void updateBankingInfo_routingNumberNonNumeric_throws() {
        service.updateBankingInfo(5, "ABCDEFGHI", "00012345678", "CHECKING");
    }

    @Test(expected = ServiceException.class)
    public void updateBankingInfo_missingAccountNumber_throws() {
        service.updateBankingInfo(5, "021000021", "", "CHECKING");
    }

    @Test(expected = ServiceException.class)
    public void updateBankingInfo_invalidAccountType_throws() {
        service.updateBankingInfo(5, "021000021", "00012345678", "MONEY_MARKET");
    }

    @Test(expected = ServiceException.class)
    public void updateBankingInfo_unknownProvider_throws() {
        when(providerDAO.findById(999)).thenReturn(null);
        service.updateBankingInfo(999, "021000021", "00012345678", "CHECKING");
    }

    @Test
    public void updateBankingInfo_invalidInput_doesNotPersistPartialData() {
        try {
            service.updateBankingInfo(5, "12345", "00012345678", "CHECKING");
        } catch (ServiceException expected) {
            // expected
        }
        verify(providerDAO, never()).updateBankingInfo(anyInt(), anyString(), anyString(), anyString());
    }
}
