package com.meridian.claims.service;

import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.model.TradingPartner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TradingPartnerServiceTest {

    private TradingPartnerService service;
    private TradingPartnerDAO tradingPartnerDAO;
    private AuditService auditService;

    @Before
    public void setUp() {
        tradingPartnerDAO = Mockito.mock(TradingPartnerDAO.class);
        auditService = Mockito.mock(AuditService.class);
        service = new TradingPartnerService(tradingPartnerDAO, auditService);
    }

    private TradingPartner validLocalPartner() {
        TradingPartner p = new TradingPartner();
        p.setPartnerName("Acme Health");
        p.setIsaQualifier("ZZ");
        p.setIsaId("ACMEID");
        p.setGsId("ACMEGS");
        p.setTransportType(TradingPartner.TRANSPORT_LOCAL);
        p.setInboundPath("/inbound");
        return p;
    }

    @Test
    public void create_validLocalPartner_succeeds() {
        TradingPartner p = validLocalPartner();
        service.create(p);
        Mockito.verify(tradingPartnerDAO).insert(p);
        Mockito.verify(auditService).record(Mockito.eq("TRADING_PARTNER_CREATED"),
            Mockito.eq("TRADING_PARTNER"), Mockito.anyLong(), Mockito.anyString());
    }

    @Test(expected = ServiceException.class)
    public void create_missingPartnerName_throws() {
        TradingPartner p = validLocalPartner();
        p.setPartnerName(" ");
        service.create(p);
    }

    @Test(expected = ServiceException.class)
    public void create_missingIsaQualifier_throws() {
        TradingPartner p = validLocalPartner();
        p.setIsaQualifier(null);
        service.create(p);
    }

    @Test(expected = ServiceException.class)
    public void create_invalidTransportType_throws() {
        TradingPartner p = validLocalPartner();
        p.setTransportType("FTP");
        service.create(p);
    }

    @Test(expected = ServiceException.class)
    public void create_sftpWithoutHost_throws() {
        TradingPartner p = validLocalPartner();
        p.setTransportType(TradingPartner.TRANSPORT_SFTP);
        p.setTransportUsername("svc");
        p.setTransportCredentialRef("ref");
        service.create(p);
    }

    @Test(expected = ServiceException.class)
    public void create_sftpWithoutCredentialRef_throws() {
        TradingPartner p = validLocalPartner();
        p.setTransportType(TradingPartner.TRANSPORT_SFTP);
        p.setTransportHost("sftp.acme.example.com");
        p.setTransportUsername("svc");
        service.create(p);
    }

    @Test
    public void create_sftpWithAllRequiredFields_succeeds() {
        TradingPartner p = validLocalPartner();
        p.setTransportType(TradingPartner.TRANSPORT_SFTP);
        p.setTransportHost("sftp.acme.example.com");
        p.setTransportUsername("svc");
        p.setTransportCredentialRef("acme-ref");
        service.create(p);
        Mockito.verify(tradingPartnerDAO).insert(p);
    }

    @Test(expected = ServiceException.class)
    public void create_missingInboundPath_throws() {
        TradingPartner p = validLocalPartner();
        p.setInboundPath(null);
        service.create(p);
    }

    @Test
    public void deactivate_setsActiveFalseAndUpdates() {
        TradingPartner existing = validLocalPartner();
        existing.setId(5);
        existing.setActive(true);
        Mockito.when(tradingPartnerDAO.findById(5)).thenReturn(existing);

        service.deactivate(5);

        Mockito.verify(tradingPartnerDAO).update(Mockito.argThat(p -> !p.isActive()));
    }

    @Test(expected = ServiceException.class)
    public void findById_notFound_throws() {
        Mockito.when(tradingPartnerDAO.findById(99)).thenReturn(null);
        service.findById(99);
    }
}
