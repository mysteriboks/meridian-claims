package com.meridian.claims.transport;

import com.meridian.claims.model.TradingPartner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalDirectoryTransportAdapterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LocalDirectoryTransportAdapter adapter;
    private Path inbound;
    private Path outbound;
    private TradingPartner partner;

    @Before
    public void setUp() throws Exception {
        adapter = new LocalDirectoryTransportAdapter();
        inbound = tmp.newFolder("inbound").toPath();
        outbound = tmp.newFolder("outbound").toPath();

        partner = new TradingPartner();
        partner.setPartnerName("Test Partner");
        partner.setInboundPath(inbound.toString());
        partner.setOutboundPath(outbound.toString());
    }

    @Test
    public void listInboundFiles_returnsOnlyRegularFiles() throws Exception {
        Files.write(inbound.resolve("claim1.edi"), "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(inbound.resolve("claim2.json"), "content2".getBytes(StandardCharsets.UTF_8));
        Files.createDirectory(inbound.resolve("subdir"));

        List<String> files = adapter.listInboundFiles(partner);

        assertEquals(2, files.size());
        assertTrue(files.contains("claim1.edi"));
        assertTrue(files.contains("claim2.json"));
    }

    @Test
    public void readInboundFile_returnsUtf8Content() throws Exception {
        Files.write(inbound.resolve("claim1.edi"), "ISA*00*hello".getBytes(StandardCharsets.UTF_8));

        String content = adapter.readInboundFile(partner, "claim1.edi");

        assertEquals("ISA*00*hello", content);
    }

    @Test
    public void archiveInboundFile_accepted_movesToArchiveSubdir() throws Exception {
        Files.write(inbound.resolve("claim1.edi"), "x".getBytes(StandardCharsets.UTF_8));

        adapter.archiveInboundFile(partner, "claim1.edi", true);

        assertFalse(Files.exists(inbound.resolve("claim1.edi")));
        assertTrue(Files.exists(inbound.resolve("archive").resolve("claim1.edi")));
    }

    @Test
    public void archiveInboundFile_rejected_movesToRejectedSubdir() throws Exception {
        Files.write(inbound.resolve("claim1.edi"), "x".getBytes(StandardCharsets.UTF_8));

        adapter.archiveInboundFile(partner, "claim1.edi", false);

        assertFalse(Files.exists(inbound.resolve("claim1.edi")));
        assertTrue(Files.exists(inbound.resolve("rejected").resolve("claim1.edi")));
    }

    @Test
    public void writeOutboundFile_writesToOutboundPath() throws Exception {
        adapter.writeOutboundFile(partner, "ack.999", "999-content");

        Path written = outbound.resolve("ack.999");
        assertTrue(Files.exists(written));
        assertEquals("999-content", new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test
    public void writeOutboundFile_noOutboundPathConfigured_isNoOp() throws Exception {
        partner.setOutboundPath(null);

        adapter.writeOutboundFile(partner, "ack.999", "999-content");
        // No exception; nothing written anywhere observable — the test just documents
        // that a missing outbound path is a safe no-op, not a failure.
    }

    @Test(expected = TransportException.class)
    public void readInboundFile_noInboundPathConfigured_throws() throws Exception {
        partner.setInboundPath(null);
        adapter.readInboundFile(partner, "claim1.edi");
    }

    @Test(expected = TransportException.class)
    public void readInboundFile_missingFile_throws() throws Exception {
        adapter.readInboundFile(partner, "does-not-exist.edi");
    }
}
