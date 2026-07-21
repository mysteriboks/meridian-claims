package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Edi835Generator.
 *
 * Each test uses a mocked ClaimDAO to avoid any database dependency.
 * The ClaimDAO stub returns Claim objects whose claimNumber is set to a
 * recognisable string so assertions are deterministic.
 */
public class Edi835GeneratorTest {

    private Edi835Generator generator;
    private ClaimDAO claimDAO;

    @Before
    public void setUp() throws Exception {
        claimDAO   = mock(ClaimDAO.class);
        generator  = new Edi835Generator();

        // Inject the mock via the package-private field set via reflection
        // (Spring would @Autowire this in production).
        java.lang.reflect.Field daoField = Edi835Generator.class.getDeclaredField("claimDAO");
        daoField.setAccessible(true);
        daoField.set(generator, claimDAO);

        // Leave ediOutputPath blank (no disk writes during tests).
        java.lang.reflect.Field pathField = Edi835Generator.class.getDeclaredField("ediOutputPath");
        pathField.setAccessible(true);
        pathField.set(generator, "");

        // Stub claims referenced in item 1 and item 2.
        Claim claim1 = new Claim();
        claim1.setId(1);
        claim1.setClaimNumber("CLM-0001");
        when(claimDAO.findById(1)).thenReturn(claim1);

        Claim claim2 = new Claim();
        claim2.setId(2);
        claim2.setClaimNumber("CLM-0002");
        when(claimDAO.findById(2)).thenReturn(claim2);
    }

    // -------------------------------------------------------------------------
    // Test 1: basic 835 structure and content
    // -------------------------------------------------------------------------

    @Test
    public void generate_producesValid835() throws Exception {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(1);
        batch.setPaymentDate(new SimpleDateFormat("yyyy-MM-dd").parse("2026-06-30"));
        batch.setTotalPaid(new BigDecimal("240.00"));

        RemittanceBatchItem item1 = new RemittanceBatchItem();
        item1.setId(10);
        item1.setClaimId(1);
        item1.setBilled(new BigDecimal("200.00"));
        item1.setPlanPaid(new BigDecimal("160.00"));
        item1.setAdjustmentReasonCode("CO-45");

        RemittanceBatchItem item2 = new RemittanceBatchItem();
        item2.setId(11);
        item2.setClaimId(2);
        item2.setBilled(new BigDecimal("100.00"));
        item2.setPlanPaid(new BigDecimal("80.00"));
        item2.setAdjustmentReasonCode("CO-97");

        List<RemittanceBatchItem> items = new ArrayList<RemittanceBatchItem>();
        items.add(item1);
        items.add(item2);

        String result = generator.generate(batch, items);

        assertNotNull("Result must not be null", result);
        assertTrue("Must contain ST*835",    result.contains("ST"));
        assertTrue("Must contain BPR",       result.contains("BPR"));
        // Sum of planPaid: 160.00 + 80.00 = 240.00
        assertTrue("Must contain total paid 240.00", result.contains("240.00"));
        assertTrue("Must contain CLP",       result.contains("CLP"));
        assertTrue("Must contain CAS",       result.contains("CAS"));
        assertTrue("Must contain CO-45",     result.contains("CO-45"));
        assertTrue("Must contain CO-97",     result.contains("CO-97"));
        assertTrue("Must contain SE",        result.contains("SE"));
        assertTrue("Must contain IEA",       result.contains("IEA"));
    }

    // -------------------------------------------------------------------------
    // Test 2: round-trip — StAEDI must be able to parse what we generated
    // -------------------------------------------------------------------------

    @Test
    public void generate_roundTrip_parsesWithStaEDI() throws Exception {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(42);
        batch.setPaymentDate(new Date());
        batch.setTotalPaid(new BigDecimal("500.00"));

        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setId(1);
        item.setClaimId(1);
        item.setBilled(new BigDecimal("500.00"));
        item.setPlanPaid(new BigDecimal("500.00"));
        item.setAdjustmentReasonCode(null);

        List<RemittanceBatchItem> items = new ArrayList<RemittanceBatchItem>();
        items.add(item);

        String edi = generator.generate(batch, items);
        assertNotNull("Generated EDI must not be null", edi);

        // Parse the generated 835 with StAEDI — must not throw any EDIStreamException.
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

        assertTrue("Parser must reach END_INTERCHANGE",
            EDIStreamEvent.END_INTERCHANGE == lastEvent);
    }

    // -------------------------------------------------------------------------
    // Test 3: money precision — BigDecimal values must appear verbatim
    // -------------------------------------------------------------------------

    @Test
    public void generate_moneyPrecision() throws Exception {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(99);
        batch.setPaymentDate(new Date());
        batch.setTotalPaid(new BigDecimal("123.45"));

        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setId(1);
        item.setClaimId(1);
        item.setBilled(new BigDecimal("200.00"));
        item.setPlanPaid(new BigDecimal("123.45"));
        item.setAdjustmentReasonCode("CO-1");

        List<RemittanceBatchItem> items = new ArrayList<RemittanceBatchItem>();
        items.add(item);

        String result = generator.generate(batch, items);

        assertNotNull("Result must not be null", result);
        // planPaid must appear as plain string
        assertTrue("Must contain planPaid 123.45", result.contains("123.45"));
        // adjustment = billed - planPaid = 200.00 - 123.45 = 76.55
        assertTrue("Must contain adjustment amount 76.55", result.contains("76.55"));
    }
}
