package com.meridian.claims.transport;

import com.meridian.claims.model.TradingPartner;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Exercises SftpTransportAdapter against a real (embedded) SFTP server — proves
 * the JSch integration actually speaks the SFTP protocol correctly, not just
 * that the code compiles. No live trading partner is needed; MINA SSHD stands
 * in for one, matching the project's precedent of an in-process fake for
 * anything that would otherwise need a real external endpoint (H2 for
 * PostgreSQL in DAO ITs, LoggingMailService for SMTP in dev).
 */
public class SftpTransportAdapterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private SshServer sshServer;
    private Path root;
    private SftpTransportAdapter adapter;
    private TradingPartner partner;

    private static final String USERNAME = "meridian-svc";
    private static final String PASSWORD = "s3cr3t-test-password";
    private static final String CREDENTIAL_REF = "embedded-test";

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("sftp-root").toPath();
        Files.createDirectories(root.resolve("inbound"));
        Files.createDirectories(root.resolve("outbound"));

        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0); // ephemeral
        sshServer.setKeyPairProvider(
            new SimpleGeneratorHostKeyProvider(tmp.newFile("hostkey.ser").toPath()));
        sshServer.setPasswordAuthenticator((username, password, session) ->
            USERNAME.equals(username) && PASSWORD.equals(password));
        sshServer.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(root));
        sshServer.start();

        Environment environment = Mockito.mock(Environment.class);
        when(environment.getProperty("claims.transport.credential." + CREDENTIAL_REF)).thenReturn(PASSWORD);
        adapter = new SftpTransportAdapter(environment);

        partner = new TradingPartner();
        partner.setPartnerName("Embedded Test Partner");
        partner.setTransportHost("localhost");
        partner.setTransportPort(sshServer.getPort());
        partner.setTransportUsername(USERNAME);
        partner.setTransportCredentialRef(CREDENTIAL_REF);
        partner.setInboundPath("/inbound");
        partner.setOutboundPath("/outbound");
    }

    @After
    public void tearDown() throws Exception {
        if (sshServer != null) {
            sshServer.stop(true);
        }
    }

    @Test
    public void listInboundFiles_returnsFilesFromRemoteDirectory() throws Exception {
        Files.write(root.resolve("inbound/claim1.edi"), "content".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("inbound/claim2.edi"), "content".getBytes(StandardCharsets.UTF_8));

        List<String> files = adapter.listInboundFiles(partner);

        assertEquals(2, files.size());
        assertTrue(files.contains("claim1.edi"));
        assertTrue(files.contains("claim2.edi"));
    }

    @Test
    public void readInboundFile_returnsExactContentOverSftp() throws Exception {
        Files.write(root.resolve("inbound/claim1.edi"), "ISA*00*hello-over-sftp".getBytes(StandardCharsets.UTF_8));

        String content = adapter.readInboundFile(partner, "claim1.edi");

        assertEquals("ISA*00*hello-over-sftp", content);
    }

    @Test
    public void archiveInboundFile_accepted_movesRemoteFileToArchiveSubdir() throws Exception {
        Files.write(root.resolve("inbound/claim1.edi"), "x".getBytes(StandardCharsets.UTF_8));

        adapter.archiveInboundFile(partner, "claim1.edi", true);

        assertFalse(Files.exists(root.resolve("inbound/claim1.edi")));
        assertTrue(Files.exists(root.resolve("inbound/archive/claim1.edi")));
    }

    @Test
    public void archiveInboundFile_rejected_movesRemoteFileToRejectedSubdir() throws Exception {
        Files.write(root.resolve("inbound/claim1.edi"), "x".getBytes(StandardCharsets.UTF_8));

        adapter.archiveInboundFile(partner, "claim1.edi", false);

        assertFalse(Files.exists(root.resolve("inbound/claim1.edi")));
        assertTrue(Files.exists(root.resolve("inbound/rejected/claim1.edi")));
    }

    @Test
    public void writeOutboundFile_writesContentToRemoteOutboundDir() throws Exception {
        adapter.writeOutboundFile(partner, "ack.999", "999-ack-content");

        Path written = root.resolve("outbound/ack.999");
        assertTrue(Files.exists(written));
        assertEquals("999-ack-content", new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test(expected = TransportException.class)
    public void wrongPassword_throwsTransportException() throws Exception {
        Environment badEnv = Mockito.mock(Environment.class);
        when(badEnv.getProperty("claims.transport.credential." + CREDENTIAL_REF)).thenReturn("wrong-password");
        SftpTransportAdapter badAdapter = new SftpTransportAdapter(badEnv);

        badAdapter.listInboundFiles(partner);
    }

    @Test(expected = TransportException.class)
    public void missingCredential_throwsTransportExceptionBeforeConnecting() throws Exception {
        Environment emptyEnv = Mockito.mock(Environment.class);
        SftpTransportAdapter noCredAdapter = new SftpTransportAdapter(emptyEnv);

        noCredAdapter.listInboundFiles(partner);
    }
}
