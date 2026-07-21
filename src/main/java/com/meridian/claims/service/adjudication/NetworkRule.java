package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.NetworkStatus;

public class NetworkRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "NetworkRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        NetworkStatus networkStatus = ctx.getProvider().getNetworkStatus();
        boolean inNetwork = NetworkStatus.IN_NETWORK.equals(networkStatus);
        if (inNetwork) {
            ctx.setNetworkCoveragePct(ctx.getPlan().getCoveragePctInNetwork());
            return AdjudicationRuleResult.passed("Provider is in-network; coverage " + ctx.getPlan().getCoveragePctInNetwork() + "%");
        } else {
            ctx.setNetworkCoveragePct(ctx.getPlan().getCoveragePctOutNetwork());
            return AdjudicationRuleResult.passed("Provider is out-of-network; coverage " + ctx.getPlan().getCoveragePctOutNetwork() + "%");
        }
    }
}
