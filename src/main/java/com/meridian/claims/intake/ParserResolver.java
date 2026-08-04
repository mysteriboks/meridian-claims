package com.meridian.claims.intake;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link ClaimFileParser} appropriate for a file name, by extension.
 * The single home for this dispatch logic — both {@code InboundClaimFilePollerJob}
 * (global directory) and {@code TradingPartnerPollerJob} (Phase 13, per-partner
 * transport) use it, so the extension-to-parser mapping is defined once.
 */
@Component
public class ParserResolver {

    private final FhirClaimFileParser fhirParser;
    private final X12Edi837Parser x12Parser;

    @Value("${claims.intake.edi.extensions:edi,x12,837}")
    private String ediExtensions;

    @Autowired
    public ParserResolver(FhirClaimFileParser fhirParser, X12Edi837Parser x12Parser) {
        this.fhirParser = fhirParser;
        this.x12Parser = x12Parser;
    }

    /**
     * Return the parser appropriate for the given file name, or null if the
     * extension is not recognised (caller should skip the file).
     *
     * - .json files are handled by FhirClaimFileParser.
     * - Files whose extension (case-insensitive) matches any token in
     *   claims.intake.edi.extensions are handled by X12Edi837Parser.
     */
    public ClaimFileParser resolve(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        if ("json".equals(ext)) {
            return fhirParser;
        }
        String[] ediExts = ediExtensions.split(",");
        for (int i = 0; i < ediExts.length; i++) {
            if (ediExts[i].trim().toLowerCase().equals(ext)) {
                return x12Parser;
            }
        }
        return null;
    }
}
