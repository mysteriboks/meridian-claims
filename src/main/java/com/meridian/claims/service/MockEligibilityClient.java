package com.meridian.claims.service;

import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Provider;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Dev-safe default {@link EligibilityClient} — the same role
 * {@link LoggingMailService} plays for {@link MailService}: no real
 * clearinghouse connectivity exists yet (Phase 16 needs a contracted
 * trading partner), so this synthesizes a realistic X12 271 response from
 * Meridian's own {@link MemberCoverageDAO} data rather than calling out.
 * The 270 request text is accepted (to honor the {@link EligibilityClient}
 * contract a real implementation would need) but not transmitted anywhere.
 */
@Service
public class MockEligibilityClient implements EligibilityClient {

    private static final Logger LOG = Logger.getLogger(MockEligibilityClient.class);

    @Autowired
    private MemberCoverageDAO memberCoverageDAO;

    @Override
    public String checkEligibility(Member member, Provider provider, String serviceType, String requestEdi270) {
        LOG.info("[MOCK-ELIGIBILITY] 270 request for memberId=" + member.getId()
                + " serviceType=" + serviceType + " — synthesizing 271 from internal coverage data");

        List<MemberCoverage> coverages = memberCoverageDAO.findActiveByMemberId(member.getId());
        MemberCoverage active = null;
        Date now = new Date();
        for (MemberCoverage c : coverages) {
            if (c.isActiveOn(now)) {
                active = c;
                break;
            }
        }
        return buildEdi271(member, active);
    }

    private String buildEdi271(Member member, MemberCoverage active) {
        String eb01 = active != null ? "1" : "6"; // 1 = active coverage, 6 = inactive
        String planName = active != null ? active.getPlanName() : "";
        Date now = new Date();
        String dateYyyyMmDd = EdiEnvelopeWriter.fmt(now, "yyyyMMdd");
        String dateYyMmDd = EdiEnvelopeWriter.fmt(now, "yyMMdd");
        String isaControlNumber = "000000001";

        StringBuilder sb = new StringBuilder();
        sb.append("ISA*00*          *00*          *ZZ*").append(EdiEnvelopeWriter.RECEIVER_ID)
          .append("*ZZ*").append(EdiEnvelopeWriter.SENDER_ID).append('*').append(dateYyMmDd)
          .append("*0000*^*00501*").append(isaControlNumber).append("*0*T*:~");
        sb.append("GS*HB*EXTERNAL*MERIDIAN*").append(dateYyyyMmDd).append("*0000*1*X*005010X279A1~");
        sb.append("ST*271*0001~");
        sb.append("BHT*0022*11*").append(member.getId()).append('*').append(dateYyyyMmDd).append("*0000~");
        sb.append("NM1*IL*1*").append(nz(member.getLastName())).append('*').append(nz(member.getFirstName()))
          .append("****MI*").append(nz(member.getMemberNumber())).append("~");
        sb.append("EB*").append(eb01).append("*IND*30**").append(nz(planName)).append("~");
        sb.append("MSG*").append(active != null ? "Coverage active" : "Coverage not active").append("~");
        sb.append("SE*6*0001~");
        sb.append("GE*1*1~");
        sb.append("IEA*1*").append(isaControlNumber).append("~");
        return sb.toString();
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }
}
