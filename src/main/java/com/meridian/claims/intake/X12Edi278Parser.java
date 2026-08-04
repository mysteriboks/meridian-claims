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
 * Parses X12 278 (Health Care Services Review — Request) files into
 * {@link PriorAuthRequest} objects — one per ST..SE transaction. Same flat
 * segment-dispatch style as {@link X12Edi837Parser} / {@link X12Edi276Parser}
 * (structural simplification, not full 278 implementation-guide conformance —
 * no real trading partner validates this file yet).
 *
 * Segment/element mappings (element positions are 1-based):
 *   ISA[13] / GS[6] / ST[2]     -> control numbers
 *   NM1 qualifier IL            -> member: NM1[9] = member number
 *   NM1 qualifier 1P/41/82/85   -> provider: NM1[9] = NPI
 *   UM[3]                       -> requested service type
 *   DTP qualifier 291 (range)   -> requested certification period, "CCYYMMDD-CCYYMMDD"
 *   SV1                         -> procedure code (composite HC:xxx) + requested units (element 4),
 *                                  same shape X12Edi837Parser already reads for the professional line item
 */
@Component
public class X12Edi278Parser {

    private static final Logger LOG = Logger.getLogger(X12Edi278Parser.class);

    public List<PriorAuthRequest> parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        List<PriorAuthRequest> requests = new ArrayList<PriorAuthRequest>();
        EDIInputFactory factory = EDIInputFactory.newFactory();
        EDIStreamReader reader = null;
        try {
            InputStream is = new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
            reader = factory.createEDIStreamReader(is);
            processInterchange(reader, requests);
        } catch (IntakeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IntakeParseException("278 file could not be parsed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
        return requests;
    }

    private void processInterchange(EDIStreamReader reader, List<PriorAuthRequest> requests) throws Exception {
        String isaControlNumber = null;
        String gsControlNumber = null;

        String currentSegment = null;
        int elementPosition = 0;
        int componentPosition = 0;
        boolean insideComponent = false;

        PriorAuthRequest tx = null;
        String currentNm1Qualifier = null;
        String currentDtpQualifier = null;
        String pendingCptCode = null;

        while (reader.hasNext()) {
            EDIStreamEvent event;
            try {
                event = reader.next();
            } catch (Exception e) {
                if (isaControlNumber == null) {
                    throw new IntakeParseException("278 interchange header (ISA) could not be read: " + e.getMessage(), e);
                }
                LOG.error("278 stream error after ISA " + isaControlNumber + "; abandoning remaining transactions: " + e.getMessage());
                break;
            }

            switch (event) {
                case START_INTERCHANGE:
                    currentSegment = "ISA";
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case START_GROUP:
                case END_GROUP:
                case END_INTERCHANGE:
                    break;

                case START_TRANSACTION:
                    tx = new PriorAuthRequest();
                    tx.setIsaControlNumber(isaControlNumber);
                    tx.setGsControlNumber(gsControlNumber);
                    currentSegment = null;
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_TRANSACTION:
                    if (tx != null) {
                        requests.add(tx);
                        tx = null;
                    }
                    currentSegment = null;
                    break;

                case START_SEGMENT:
                    currentSegment = reader.getText();
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_SEGMENT:
                    break;

                case START_COMPOSITE:
                    elementPosition++;
                    insideComponent = true;
                    componentPosition = 0;
                    break;

                case END_COMPOSITE:
                    insideComponent = false;
                    break;

                case ELEMENT_DATA:
                    String value = reader.getText();
                    if (insideComponent) {
                        componentPosition++;
                    } else {
                        elementPosition++;
                        componentPosition = 0;
                    }
                    if (value != null) {
                        value = value.trim();
                    }

                    if ("ISA".equals(currentSegment) && elementPosition == 13 && !insideComponent) {
                        isaControlNumber = value;
                    } else if ("GS".equals(currentSegment) && elementPosition == 6 && !insideComponent) {
                        gsControlNumber = value;
                    } else if ("ST".equals(currentSegment) && elementPosition == 2 && !insideComponent && tx != null) {
                        tx.setStControlNumber(value);
                    } else if (tx != null && "NM1".equals(currentSegment)) {
                        if (elementPosition == 1 && !insideComponent) {
                            currentNm1Qualifier = value;
                        } else if (elementPosition == 9 && !insideComponent) {
                            if ("IL".equalsIgnoreCase(currentNm1Qualifier)) {
                                tx.setMemberNumber(value);
                            } else if ("1P".equals(currentNm1Qualifier) || "41".equals(currentNm1Qualifier)
                                    || "82".equals(currentNm1Qualifier) || "85".equals(currentNm1Qualifier)) {
                                tx.setProviderNpi(value);
                            }
                        }
                    } else if (tx != null && "UM".equals(currentSegment) && elementPosition == 3 && !insideComponent) {
                        tx.setServiceType(value);
                    } else if (tx != null && "DTP".equals(currentSegment)) {
                        if (elementPosition == 1 && !insideComponent) {
                            currentDtpQualifier = value;
                        } else if (elementPosition == 3 && !insideComponent && "291".equals(currentDtpQualifier)) {
                            int dash = value.indexOf('-');
                            if (dash > 0) {
                                tx.setAuthorizedFromString(value.substring(0, dash));
                                tx.setAuthorizedToString(value.substring(dash + 1));
                            } else {
                                tx.setAuthorizedFromString(value);
                                tx.setAuthorizedToString(value);
                            }
                        }
                    } else if (tx != null && "SV1".equals(currentSegment)) {
                        if (elementPosition == 1) {
                            if (insideComponent && componentPosition == 2) {
                                pendingCptCode = value;
                            } else if (!insideComponent) {
                                int colonIdx = value.indexOf(':');
                                pendingCptCode = colonIdx >= 0 ? value.substring(colonIdx + 1) : value;
                            }
                        } else if (elementPosition == 4 && !insideComponent) {
                            if (pendingCptCode != null && !pendingCptCode.isEmpty()) {
                                tx.setProcedureCode(pendingCptCode);
                            }
                            if (!value.isEmpty()) {
                                try {
                                    tx.setRequestedUnits(Integer.valueOf(value));
                                } catch (NumberFormatException ignored) {
                                    // leave requestedUnits unset
                                }
                            }
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }
}
