package com.smartassign.routing;

import com.smartassign.common.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final RoutingService routing;

    public RoutingController(RoutingService routing) {
        this.routing = routing;
    }

    /** Step B: rule-by-rule hard eligibility for every handler. */
    @PostMapping("/{claimId}/eligibility")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public RoutingDtos.EligibilityResponse eligibility(@PathVariable Long claimId) {
        return routing.checkEligibility(claimId, CurrentUser.username());
    }

    /** Step C: eligibility + ranking of eligible handlers only (or safe fallback to manual review). */
    @PostMapping("/{claimId}/recommendations")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public RoutingDtos.RecommendationResponse recommend(@PathVariable Long claimId) {
        return routing.recommend(claimId, CurrentUser.username());
    }

    @GetMapping("/{claimId}/runs")
    public List<RoutingDtos.RunSummary> runs(@PathVariable Long claimId) {
        return routing.runsForClaim(claimId);
    }

    @GetMapping("/runs/{runId}")
    public RoutingDtos.RecommendationResponse run(@PathVariable Long runId) {
        return routing.getRun(runId);
    }
}
