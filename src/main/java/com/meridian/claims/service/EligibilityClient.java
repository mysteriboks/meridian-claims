package com.meridian.claims.service;

import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;

/**
 * Transport boundary for real-time eligibility (X12 270/271) round trips
 * (Phase 16) — mirrors {@link MailService}'s role as the seam between
 * business logic and an external channel. The only implementation today is
 * {@link MockEligibilityClient}: a live clearinghouse round trip needs a
 * contracted trading partner Meridian does not yet have, so — unlike mail,
 * which has a real SMTP path behind a flag — there is no real path to gate
 * behind a flag yet. Wiring a real implementation is a future-phase change,
 * not a config toggle on this one.
 */
public interface EligibilityClient {

    /**
     * Sends the outbound 270 request and returns the raw 271 response EDI text.
     *
     * @param member         the member being checked
     * @param provider       the requesting provider, may be null for a member-only check
     * @param serviceType    EQ01 service type code (e.g. "30" = general coverage)
     * @param requestEdi270  the outbound 270 EDI text, as built by {@link Edi270Generator}
     */
    String checkEligibility(Member member, Provider provider, String serviceType, String requestEdi270)
            throws EligibilityClientException;
}
