package com.meridian.claims.service;

import com.meridian.claims.dao.ReportDAO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ReportServiceTest {

    private ReportService reportService;
    private ReportDAO reportDAO;

    @Before
    public void setUp() {
        reportService = new ReportService();
        reportDAO = Mockito.mock(ReportDAO.class);
        ReflectionTestUtils.setField(reportService, "reportDAO", reportDAO);
    }

    @Test
    public void claimCountsByStatus_delegatesToDAO() {
        Map<String, Long> expected = new LinkedHashMap<String, Long>();
        expected.put("APPROVED", 5L);
        Mockito.when(reportDAO.claimCountsByStatus()).thenReturn(expected);

        Map<String, Long> result = reportService.claimCountsByStatus();
        assertEquals(expected, result);
    }

    @Test
    public void toCsv_emptyList_returnsEmptyString() {
        String csv = reportService.toCsv(Collections.<Map<String, Object>>emptyList());
        assertEquals("", csv);
    }

    @Test
    public void toCsv_singleRow_producesHeaderAndRow() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("claim_number", "CLM-001");
        row.put("status", "APPROVED");
        row.put("amount", "144.00");

        String csv = reportService.toCsv(Arrays.asList(row));
        String[] lines = csv.split("\r\n");
        assertEquals("claim_number,status,amount", lines[0]);
        assertEquals("CLM-001,APPROVED,144.00", lines[1]);
    }

    @Test
    public void toCsv_cellWithComma_isQuoted() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", "Smith, John");
        row.put("val", "100");

        String csv = reportService.toCsv(Arrays.asList(row));
        assertTrue(csv.contains("\"Smith, John\""));
    }

    @Test
    public void toCsv_nullCell_becomesEmpty() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("a", null);
        row.put("b", "value");

        String csv = reportService.toCsv(Arrays.asList(row));
        assertTrue(csv.contains(",value"));
    }

    @Test
    public void claimsDetail_returnsPaginatedPage() {
        List<Map<String, Object>> items = Arrays.asList(new LinkedHashMap<String, Object>());
        Mockito.when(reportDAO.claimsDetail(
            Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
            Mockito.eq(1), Mockito.eq(50))).thenReturn(items);
        Mockito.when(reportDAO.claimsDetailCount(
            Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull()))
            .thenReturn(1L);

        com.meridian.claims.util.Page<Map<String, Object>> page =
            reportService.claimsDetail(new Date(), new Date(), null, null, null, 1, 50);

        assertEquals(1, page.getTotalItems());
        assertEquals(1, page.getItems().size());
    }

    @Test
    public void slaBreachCount_delegatesToDAO() {
        Mockito.when(reportDAO.slaBreachCount()).thenReturn(7L);
        assertEquals(7L, reportService.slaBreachCount());
    }
}
