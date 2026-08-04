package com.meridian.claims.transport;

import com.meridian.claims.model.TradingPartner;

import java.util.List;

/**
 * Seam interface for moving files to/from a trading partner (Phase 13).
 *
 * Implementations know how to reach one transport type — {@link LocalDirectoryTransportAdapter}
 * (the default; wraps a local filesystem directory, matching what
 * {@code InboundClaimFilePollerJob} has always done for the single global intake
 * path) and {@link SftpTransportAdapter} (JSch-based, for a real trading partner).
 * {@link TransportAdapterResolver} picks the right implementation per partner from
 * {@link TradingPartner#getTransportType()}.
 *
 * All methods operate on file *names*, not paths — each adapter resolves the
 * partner's configured inbound/outbound path internally, so callers never build
 * partner-specific paths themselves.
 */
public interface TransportAdapter {

    /** List file names currently available in the partner's inbound location. */
    List<String> listInboundFiles(TradingPartner partner) throws TransportException;

    /** Read the full text content (UTF-8) of one inbound file. */
    String readInboundFile(TradingPartner partner, String fileName) throws TransportException;

    /**
     * Move a processed inbound file out of the polling location so it is not
     * picked up again — mirrors the archive/rejected move the existing
     * {@code InboundClaimFilePollerJob} performs for the local-directory case.
     */
    void archiveInboundFile(TradingPartner partner, String fileName, boolean accepted) throws TransportException;

    /** Write an outbound file (e.g. a 999/277CA acknowledgment) to the partner's outbound location. */
    void writeOutboundFile(TradingPartner partner, String fileName, String content) throws TransportException;
}
