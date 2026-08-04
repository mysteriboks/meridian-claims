package com.meridian.claims.intake;

import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses X12 276 (Health Care Claim Status Request) files into {@link ClaimStatusInquiry}
 * objects — one per ST..SE transaction, mirroring {@link X12Edi837Parser}'s flat
 * segment-dispatch approach rather than the full HL-loop hierarchy a real 276
 * conformance test would require (same structural-simplification precedent as
 * {@code Edi999Generator}/{@code Edi277CaGenerator} — see PHASES.md Phase 13/14).
 *
 * Segment/element mappings (element positions are 1-based):
 *   ISA[13]           -> isaControlNumber
 *   GS[6]              -> gsControlNumber
 *   ST[2]              -> stControlNumber
 *   NM1 qualifier IL   -> member: NM1[9] = member number
 *   NM1 qualifier 1P/41/82/85 -> provider: NM1[9] = NPI
 *   REF qualifier 1K   -> payer claim control number (our own claim number)
 *   DTP qualifier 472  -> date of service
 *
 * This is a read-only inquiry, not a data-mutating submission — a malformed individual
 * transaction is simply skipped (logged), not quarantined through any ledger. Only a
 * structurally unparseable interchange (bad ISA) is a file-level failure.
 */
@Component
public class X12Edi276Parser {

    private static final Logger LOG = Logger.getLogger(X12Edi276Parser.class);

    public List<ClaimStatusInquiry> parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        List<ClaimStatusInquiry> inquiries = new ArrayList<ClaimStatusInquiry>();
        EDIInputFactory factory = EDIInputFactory.newFactory();
        EDIStreamReader reader = null;
        try {
            InputStream is = new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
            reader = factory.createEDIStreamReader(is);
            processInterchange(reader, inquiries);
        } catch (IntakeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IntakeParseException("276 file could not be parsed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
        return inquiries;
    }

    private void processInterchange(EDIStreamReader reader, List<ClaimStatusInquiry> inquiries) throws Exception {
        String isaControlNumber = null;
        String gsControlNumber = null;

        String currentSegment = null;
        int elementPosition = 0;

        ClaimStatusInquiry tx = null;
        String currentNm1Qualifier = null;
        String currentRefQualifier = null;
        String currentDtpQualifier = null;

        while (reader.hasNext()) {
            EDIStreamEvent event;
            try {
                event = reader.next();
            } catch (Exception e) {
                if (isaControlNumber == null) {
                    throw new IntakeParseException("276 interchange header (ISA) could not be read: " + e.getMessage(), e);
                }
                LOG.error("276 stream error after ISA " + isaControlNumber + "; abandoning remaining transactions: " + e.getMessage());
                break;
            }

            switch (event) {
                case START_INTERCHANGE:
                    currentSegment = "ISA";
                    elementPosition = 0;
                    break;

                case START_GROUP:
                case END_GROUP:
                case END_INTERCHANGE:
                    break;

                case START_TRANSACTION:
                    tx = new ClaimStatusInquiry();
                    tx.setIsaControlNumber(isaControlNumber);
                    tx.setGsControlNumber(gsControlNumber);
                    currentSegment = null;
                    elementPosition = 0;
                    break;

                case END_TRANSACTION:
                    if (tx != null) {
                        inquiries.add(tx);
                        tx = null;
                    }
                    currentSegment = null;
                    break;

                case START_SEGMENT:
                    currentSegment = reader.getText();
                    elementPosition = 0;
                    break;

                case END_SEGMENT:
                    break;

                case START_COMPOSITE:
                    elementPosition++;
                    break;

                case END_COMPOSITE:
                    break;

                case ELEMENT_DATA:
                    String value = reader.getText();
                    elementPosition++;
                    if (value != null) {
                        value = value.trim();
                    }

                    if ("ISA".equals(currentSegment) && elementPosition == 13) {
                        isaControlNumber = value;
                    } else if ("GS".equals(currentSegment) && elementPosition == 6) {
                        gsControlNumber = value;
                    } else if ("ST".equals(currentSegment) && elementPosition == 2 && tx != null) {
                        tx.setStControlNumber(value);
                    } else if (tx != null && "NM1".equals(currentSegment)) {
                        if (elementPosition == 1) {
                            currentNm1Qualifier = value;
                        } else if (elementPosition == 9) {
                            if ("IL".equalsIgnoreCase(currentNm1Qualifier)) {
                                tx.setMemberNumber(value);
                            } else if ("1P".equals(currentNm1Qualifier) || "41".equals(currentNm1Qualifier)
                                    || "82".equals(currentNm1Qualifier) || "85".equals(currentNm1Qualifier)) {
                                tx.setProviderNpi(value);
                            }
                        }
                    } else if (tx != null && "REF".equals(currentSegment)) {
                        if (elementPosition == 1) {
                            currentRefQualifier = value;
                        } else if (elementPosition == 2 && "1K".equals(currentRefQualifier)) {
                            tx.setClaimControlNumber(value);
                        }
                    } else if (tx != null && "DTP".equals(currentSegment)) {
                        if (elementPosition == 1) {
                            currentDtpQualifier = value;
                        } else if (elementPosition == 3 && "472".equals(currentDtpQualifier)) {
                            tx.setDateOfServiceString(value);
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }
}
