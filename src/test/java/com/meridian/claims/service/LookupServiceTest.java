package com.meridian.claims.service;

import com.meridian.claims.dao.LookupDAO;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ServiceTypeCategory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LookupServiceTest {

    private LookupDAO dao;
    private LookupService service;

    @Before
    public void setUp() {
        dao = mock(LookupDAO.class);
        when(dao.findAllDenialReasonCodes()).thenReturn(new ArrayList<com.meridian.claims.model.DenialReasonCode>());
        when(dao.findAllProcedureCodes()).thenReturn(new ArrayList<com.meridian.claims.model.ProcedureCode>());
        when(dao.findAllServiceTypeCategories()).thenReturn(new ArrayList<ServiceTypeCategory>());

        List<DiagnosisCode> diags = new ArrayList<DiagnosisCode>();
        diags.add(diag("I10", "Hypertension"));
        when(dao.findAllDiagnosisCodes()).thenReturn(diags);

        service = new LookupService(dao);
        service.refreshAll(); // simulate @PostConstruct
    }

    private DiagnosisCode diag(String code, String desc) {
        DiagnosisCode c = new DiagnosisCode();
        c.setCode(code);
        c.setDescription(desc);
        c.setActive(true);
        return c;
    }

    @Test
    public void listServedFromCacheNotDaoOnEachCall() {
        // refreshAll in setUp already called each findAll* exactly once.
        service.listDiagnosisCodes();
        service.listDiagnosisCodes();
        service.listDiagnosisCodes();
        // Still only the single load from refreshAll — reads do not hit the DAO.
        verify(dao, times(1)).findAllDiagnosisCodes();
    }

    @Test
    public void cachedListReturnsSeededData() {
        assertEquals(1, service.listDiagnosisCodes().size());
        assertEquals("I10", service.listDiagnosisCodes().get(0).getCode());
    }

    @Test
    public void getByCodeReadsFromCache() {
        assertNotNull(service.getDiagnosisCode("I10"));
        assertNull(service.getDiagnosisCode("NOPE"));
        // getDiagnosisCode must not query the DAO per-call
        verify(dao, times(1)).findAllDiagnosisCodes();
    }

    @Test
    public void refreshAllReloadsFromDao() {
        service.refreshAll(); // explicit admin refresh
        // setUp's refresh + this one = 2 loads
        verify(dao, times(2)).findAllDiagnosisCodes();
    }

    @Test
    public void saveDiagnosisRefreshesThatSlice() {
        when(dao.findDiagnosisCode("E11.9")).thenReturn(null);
        service.saveDiagnosisCode(diag("E11.9", "Diabetes"));
        verify(dao).insertDiagnosisCode(any(DiagnosisCode.class));
        // save triggers a reload of the diagnosis slice (setUp + this save = 2)
        verify(dao, times(2)).findAllDiagnosisCodes();
    }
}
