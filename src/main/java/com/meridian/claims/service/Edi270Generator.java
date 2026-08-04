package com.meridian.claims.service;

import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import io.xlate.edi.stream.EDIOutputFactory;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamWriter;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Generates outbound X12 270 (Eligibility, Coverage or Benefit Inquiry) requests
 * (Phase 16). Meridian is the *requester* here — the reverse direction from
 * 276/278 (Phase 14/15), where Meridian responds to inbound provider requests.
 * Same structural-simplification precedent as the rest of the EDI generators —
 * a flat NM1/EQ/DTP request, not the full HL-loop hierarchy.
 */
@Service
public class Edi270Generator {

    private static final Logger LOG = Logger.getLogger(Edi270Generator.class);

    public String generate270(Member member, Provider provider, String serviceType) {
        try {
            return buildEdi(member, provider, serviceType);
        } catch (EDIStreamException e) {
            LOG.error("EDI 270 generation failed memberId=" + member.getId(), e);
            throw new ServiceException("Failed to generate EDI 270 for member " + member.getId(), e);
        } catch (IOException e) {
            throw new ServiceException("Failed to build EDI 270 for member " + member.getId(), e);
        }
    }

    private String buildEdi(Member member, Provider provider, String serviceType)
            throws EDIStreamException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EDIOutputFactory factory = EDIOutputFactory.newFactory();
        EDIStreamWriter writer = factory.createEDIStreamWriter(baos);

        Date now = new Date();
        String dateYyyyMmDd = EdiEnvelopeWriter.fmt(now, "yyyyMMdd");
        String dateYyMmDd   = EdiEnvelopeWriter.fmt(now, "yyMMdd");
        String timeHhMm     = EdiEnvelopeWriter.fmt(now, "HHmm");
        String isaPadded = String.format("%09d", System.nanoTime() % 1000000000);

        writer.startInterchange();
        EdiEnvelopeWriter.writeIsa(writer, dateYyMmDd, timeHhMm, isaPadded, false);

        // ----- GS -----
        writer.writeStartSegment("GS");
        writer.writeElement("HS");           // GS01 functional identifier code (HS = eligibility inquiry)
        writer.writeElement("MERIDIAN");
        writer.writeElement("EXTERNAL");     // GS03 — the external payer/clearinghouse being queried
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeElement("1");
        writer.writeElement("X");
        writer.writeElement("005010X279A1"); // GS08 version/release identifier (270/271)
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("270");
        writer.writeElement("0001");
        writer.writeEndSegment();

        // ----- BHT -----
        writer.writeStartSegment("BHT");
        writer.writeElement("0022");
        writer.writeElement("13");           // BHT02 transaction set purpose code (13 = request)
        writer.writeElement(String.valueOf(member.getId()));
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeEndSegment();

        // ----- NM1*IL — subscriber (the member being checked) -----
        writer.writeStartSegment("NM1");
        writer.writeElement("IL");
        writer.writeElement("1");
        writer.writeElement(EdiEnvelopeWriter.nz(member.getLastName(), ""));
        writer.writeElement(EdiEnvelopeWriter.nz(member.getFirstName(), ""));
        writer.writeEmptyElement();
        writer.writeEmptyElement();
        writer.writeEmptyElement();
        writer.writeElement("MI");
        writer.writeElement(EdiEnvelopeWriter.nz(member.getMemberNumber(), ""));
        writer.writeEndSegment();

        // ----- NM1*1P — provider, if the check is provider-scoped -----
        if (provider != null) {
            writer.writeStartSegment("NM1");
            writer.writeElement("1P");
            writer.writeElement("2");
            writer.writeElement(EdiEnvelopeWriter.nz(provider.getName(), ""));
            writer.writeEmptyElement();
            writer.writeEmptyElement();
            writer.writeEmptyElement();
            writer.writeEmptyElement();
            writer.writeElement("XX");
            writer.writeElement(EdiEnvelopeWriter.nz(provider.getNpi(), ""));
            writer.writeEndSegment();
        }

        // ----- EQ — eligibility inquiry, service type code -----
        writer.writeStartSegment("EQ");
        writer.writeElement(EdiEnvelopeWriter.nz(serviceType, "30")); // 30 = general health benefit plan coverage
        writer.writeEndSegment();

        // ----- DTP*291 — date of inquiry -----
        writer.writeStartSegment("DTP");
        writer.writeElement("291");
        writer.writeElement("D8");
        writer.writeElement(dateYyyyMmDd);
        writer.writeEndSegment();

        // ----- SE -----
        writer.writeStartSegment("SE");
        writer.writeElement(provider != null ? "7" : "6");
        writer.writeElement("0001");
        writer.writeEndSegment();

        EdiEnvelopeWriter.writeGeAndIea(writer, isaPadded);

        writer.endInterchange();
        writer.flush();
        writer.close();

        return baos.toString("UTF-8");
    }
}
