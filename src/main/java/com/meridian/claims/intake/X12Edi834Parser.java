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
 * Parses X12 834 (Benefit Enrollment and Maintenance) files into
 * {@link EnrollmentRecord} objects — one per INS loop (one member
 * add/change/termination per loop; a single ST..SE transaction may enroll
 * many members). Same flat segment-dispatch style as
 * {@link X12Edi837Parser}/{@link X12Edi278Parser} (structural simplification,
 * not full 834 implementation-guide conformance — member identification
 * reuses this codebase's established NM1\*IL/MI convention rather than the
 * real 834 companion guide's REF\*0F subscriber-number segment).
 *
 * Segment/element mappings (element positions are 1-based):
 *   ISA[13] / GS[6] / ST[2]  -> control numbers
 *   INS[3]                   -> maintenance type code (021 add / 001 change / 024 termination)
 *   NM1 qualifier IL         -> NM1[3] = last name, NM1[4] = first name, NM1[9] = member number
 *   DMG[2]                   -> date of birth (D8, CCYYMMDD)
 *   HD[4]                    -> plan/coverage description, matched against plans.plan_name
 *   DTP qualifier 348        -> coverage effective date
 *   DTP qualifier 349        -> coverage termination date
 */
@Component
public class X12Edi834Parser {

    private static final Logger LOG = Logger.getLogger(X12Edi834Parser.class);

    public List<EnrollmentRecord> parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        List<EnrollmentRecord> records = new ArrayList<EnrollmentRecord>();
        EDIInputFactory factory = EDIInputFactory.newFactory();
        EDIStreamReader reader = null;
        try {
            InputStream is = new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
            reader = factory.createEDIStreamReader(is);
            processInterchange(reader, records);
        } catch (IntakeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IntakeParseException("834 file could not be parsed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
        return records;
    }

    private void processInterchange(EDIStreamReader reader, List<EnrollmentRecord> records) throws Exception {
        String isaControlNumber = null;
        String gsControlNumber = null;
        String stControlNumber = null;

        String currentSegment = null;
        int elementPosition = 0;
        boolean insideComponent = false;

        EnrollmentRecord rec = null;
        String currentNm1Qualifier = null;
        String currentDtpQualifier = null;

        while (reader.hasNext()) {
            EDIStreamEvent event;
            try {
                event = reader.next();
            } catch (Exception e) {
                if (isaControlNumber == null) {
                    throw new IntakeParseException("834 interchange header (ISA) could not be read: " + e.getMessage(), e);
                }
                LOG.error("834 stream error after ISA " + isaControlNumber + "; abandoning remaining records: " + e.getMessage());
                break;
            }

            switch (event) {
                case START_INTERCHANGE:
                    currentSegment = "ISA";
                    elementPosition = 0;
                    insideComponent = false;
                    break;

                case START_GROUP:
                case END_GROUP:
                case END_INTERCHANGE:
                    break;

                case START_TRANSACTION:
                    currentSegment = null;
                    elementPosition = 0;
                    insideComponent = false;
                    break;

                case END_TRANSACTION:
                    if (rec != null) {
                        records.add(rec);
                        rec = null;
                    }
                    currentSegment = null;
                    break;

                case START_SEGMENT:
                    currentSegment = reader.getText();
                    elementPosition = 0;
                    insideComponent = false;
                    // A new INS segment starts a new enrollment record; close out the
                    // one in progress (if any) first, mirroring how other parsers
                    // start a fresh carrier per repeated loop-starting segment.
                    if ("INS".equals(currentSegment)) {
                        if (rec != null) {
                            records.add(rec);
                        }
                        rec = new EnrollmentRecord();
                        rec.setIsaControlNumber(isaControlNumber);
                        rec.setGsControlNumber(gsControlNumber);
                        rec.setStControlNumber(stControlNumber);
                    }
                    break;

                case END_SEGMENT:
                    break;

                case START_COMPOSITE:
                    elementPosition++;
                    insideComponent = true;
                    break;

                case END_COMPOSITE:
                    insideComponent = false;
                    break;

                case ELEMENT_DATA:
                    String value = reader.getText();
                    if (!insideComponent) {
                        elementPosition++;
                    }
                    if (value != null) {
                        value = value.trim();
                    }

                    if ("ISA".equals(currentSegment) && elementPosition == 13 && !insideComponent) {
                        isaControlNumber = value;
                    } else if ("GS".equals(currentSegment) && elementPosition == 6 && !insideComponent) {
                        gsControlNumber = value;
                    } else if ("ST".equals(currentSegment) && elementPosition == 2 && !insideComponent) {
                        stControlNumber = value;
                    } else if (rec != null && "INS".equals(currentSegment) && elementPosition == 3 && !insideComponent) {
                        rec.setMaintenanceTypeCode(value);
                    } else if (rec != null && "NM1".equals(currentSegment)) {
                        if (elementPosition == 1 && !insideComponent) {
                            currentNm1Qualifier = value;
                        } else if ("IL".equalsIgnoreCase(currentNm1Qualifier)) {
                            if (elementPosition == 3 && !insideComponent) {
                                rec.setLastName(value);
                            } else if (elementPosition == 4 && !insideComponent) {
                                rec.setFirstName(value);
                            } else if (elementPosition == 9 && !insideComponent) {
                                rec.setMemberNumber(value);
                            }
                        }
                    } else if (rec != null && "DMG".equals(currentSegment) && elementPosition == 2 && !insideComponent) {
                        rec.setDobString(value);
                    } else if (rec != null && "HD".equals(currentSegment) && elementPosition == 4 && !insideComponent) {
                        rec.setPlanName(value);
                    } else if (rec != null && "DTP".equals(currentSegment)) {
                        if (elementPosition == 1 && !insideComponent) {
                            currentDtpQualifier = value;
                        } else if (elementPosition == 3 && !insideComponent) {
                            if ("348".equals(currentDtpQualifier)) {
                                rec.setEffectiveDateString(value);
                            } else if ("349".equals(currentDtpQualifier)) {
                                rec.setTerminationDateString(value);
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
