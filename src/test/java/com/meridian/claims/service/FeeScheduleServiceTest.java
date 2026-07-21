package com.meridian.claims.service;

import com.meridian.claims.dao.FeeScheduleRateDAO;
import com.meridian.claims.model.FeeScheduleRate;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FeeScheduleServiceTest {

    private FeeScheduleRateDAO dao;
    private AuditService auditService;
    private FeeScheduleService service;

    @Before
    public void setUp() {
        dao = mock(FeeScheduleRateDAO.class);
        auditService = mock(AuditService.class);
        service = new FeeScheduleService(dao, auditService);
    }

    @Test
    public void resolveAllowedAmountDelegatesToDAO() {
        Date dos = new Date();
        when(dao.resolveAllowedAmount(1, 2, "99213", dos)).thenReturn(new BigDecimal("120.00"));
        BigDecimal result = service.resolveAllowedAmount(1, 2, "99213", dos);
        assert new BigDecimal("120.00").compareTo(result) == 0;
    }

    @Test
    public void resolveReturnsNullWhenNoRateOnFile() {
        Date dos = new Date();
        when(dao.resolveAllowedAmount(1, null, "99999", dos)).thenReturn(null);
        assertNull(service.resolveAllowedAmount(1, null, "99999", dos));
    }

    @Test
    public void createRateInsertsRecord() {
        service.createRate(1, null, "99213", new BigDecimal("120.00"), new Date(), null);
        verify(dao).insert(any(FeeScheduleRate.class));
    }

    @Test(expected = ServiceException.class)
    public void createRateRejectsBlankCode() {
        service.createRate(1, null, "", new BigDecimal("100.00"), new Date(), null);
    }

    @Test(expected = ServiceException.class)
    public void createRateRejectsNegativeAmount() {
        service.createRate(1, null, "99213", new BigDecimal("-1.00"), new Date(), null);
    }

    @Test(expected = ServiceException.class)
    public void createRateRequiresEffectiveDate() {
        service.createRate(1, null, "99213", new BigDecimal("100.00"), null, null);
    }
}
