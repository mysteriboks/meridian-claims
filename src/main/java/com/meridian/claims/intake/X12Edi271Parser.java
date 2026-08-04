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
 * Parses X12 271 (Eligibility, Coverage or Benefit Information) responses
 * into {@link EligibilityResponse} objects — one per ST..SE transaction. Same
 * flat segment-dispatch style as {@link X12Edi278Parser} / {@link X12Edi276Parser}.
 * Meridian is the *requester* for 270/271 (Phase 16), so this parses a response
 * we received, not a request we're servicing.
 *
 * Segment/element mappings (element positions are 1-based):
 *   ISA[13] / GS[6] / ST[2]  -> control numbers
 *   NM1 qualifier IL         -> NM1[9] = member number
 *   EB[1]                    -> eligibility/benefit information code (EB01)
 *   EB[5] (composite)        -> plan/coverage description, first component
 *   MSG[1]                   -> free-text message, used as plan description fallback
 */
@Component
public class X12Edi271Parser {

    private static final Logger LOG = Logger.getLogger(X12Edi271Parser.class);

    public List<EligibilityResponse> parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        List<EligibilityResponse> responses = new ArrayList<EligibilityResponse>();
        EDIInputFactory factory = EDIInputFactory.newFactory();
        EDIStreamReader reader = null;
        try {
            InputStream is = new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
            reader = factory.createEDIStreamReader(is);
            processInterchange(reader, responses);
        } catch (IntakeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IntakeParseException("271 file could not be parsed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
        return responses;
    }

    private void processInterchange(EDIStreamReader reader, List<EligibilityResponse> responses) throws Exception {
        String isaControlNumber = null;
        String gsControlNumber = null;

        String currentSegment = null;
        int elementPosition = 0;
        int componentPosition = 0;
        boolean insideComponent = false;

        EligibilityResponse tx = null;
        String currentNm1Qualifier = null;

        while (reader.hasNext()) {
            EDIStreamEvent event;
            try {
                event = reader.next();
            } catch (Exception e) {
                if (isaControlNumber == null) {
                    throw new IntakeParseException("271 interchange header (ISA) could not be read: " + e.getMessage(), e);
                }
                LOG.error("271 stream error after ISA " + isaControlNumber + "; abandoning remaining transactions: " + e.getMessage());
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
                    tx = new EligibilityResponse();
                    tx.setIsaControlNumber(isaControlNumber);
                    tx.setGsControlNumber(gsControlNumber);
                    currentSegment = null;
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_TRANSACTION:
                    if (tx != null) {
                        responses.add(tx);
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
                        } else if (elementPosition == 9 && !insideComponent && "IL".equalsIgnoreCase(currentNm1Qualifier)) {
                            tx.setMemberNumber(value);
                        }
                    } else if (tx != null && "EB".equals(currentSegment)) {
                        if (elementPosition == 1 && !insideComponent) {
                            tx.setEb01Code(value);
                        } else if (elementPosition == 5 && !value.isEmpty()
                                && (tx.getPlanDescription() == null || tx.getPlanDescription().isEmpty())) {
                            tx.setPlanDescription(value);
                        }
                    } else if (tx != null && "MSG".equals(currentSegment) && elementPosition == 1 && !insideComponent
                            && (tx.getPlanDescription() == null || tx.getPlanDescription().isEmpty())) {
                        tx.setPlanDescription(value);
                    }
                    break;

                default:
                    break;
            }
        }
    }
}
