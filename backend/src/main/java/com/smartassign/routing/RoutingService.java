package com.smartassign.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartassign.assignment.Assignment;
import com.smartassign.assignment.AssignmentRepository;
import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.claim.Claim;
import com.smartassign.claim.ClaimDtos;
import com.smartassign.claim.ClaimRepository;
import com.smartassign.common.ApiException;
import com.smartassign.common.JsonSupport;
import com.smartassign.config.AppProperties;
import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;
import com.smartassign.core.eligibility.EligibilityDecision;
import com.smartassign.core.eligibility.EligibilityEngine;
import com.smartassign.core.eligibility.RuleCode;
import com.smartassign.core.eligibility.RuleConfig;
import com.smartassign.core.eligibility.RuleResult;
import com.smartassign.core.ml.SuitabilityScorer;
import com.smartassign.core.ml.SuitabilityScores;
import com.smartassign.core.scoring.Factor;
import com.smartassign.core.scoring.FactorScore;
import com.smartassign.core.scoring.RankingResult;
import com.smartassign.core.scoring.ScoredCandidate;
import com.smartassign.core.scoring.ScoringEngine;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.MlStatus;
import com.smartassign.handler.Handler;
import com.smartassign.handler.HandlerRepository;
import com.smartassign.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the SmartAssign pipeline for one claim:
 *   1. load claim + all handlers  2. apply hard eligibility rules  3. exclude failures
 *   4. (eligible only) request predicted fit  5. explainable ranking + fairness control
 *   6. persist run, rule-by-rule results and ranked recommendations  7. audit
 * If no handler is eligible the claim is routed to manual review (safe fallback) - rules are never relaxed.
 */
@Service
public class RoutingService {

    private final ClaimRepository claims;
    private final HandlerRepository handlers;
    private final SettingsService settings;
    private final EligibilityEngine eligibility;
    private final ScoringEngine scoring;
    private final SuitabilityScorer suitability;
    private final RecommendationRunRepository runs;
    private final EligibilityResultRepository eligibilityResults;
    private final AssignmentRecommendationRepository recommendations;
    private final AssignmentRepository assignments;
    private final AuditService audit;
    private final JsonSupport json;
    private final int validityMinutes;
    private final com.smartassign.claim.ClaimHistory history;
    private final com.smartassign.auth.UserRepository users;

    public RoutingService(ClaimRepository claims, HandlerRepository handlers, SettingsService settings,
                          EligibilityEngine eligibility, ScoringEngine scoring, SuitabilityScorer suitability,
                          RecommendationRunRepository runs, EligibilityResultRepository eligibilityResults,
                          AssignmentRecommendationRepository recommendations, AssignmentRepository assignments,
                          AuditService audit, JsonSupport json, AppProperties props,
                          com.smartassign.claim.ClaimHistory history, com.smartassign.auth.UserRepository users) {
        this.history = history;
        this.users = users;
        this.claims = claims;
        this.handlers = handlers;
        this.settings = settings;
        this.eligibility = eligibility;
        this.scoring = scoring;
        this.suitability = suitability;
        this.runs = runs;
        this.eligibilityResults = eligibilityResults;
        this.recommendations = recommendations;
        this.assignments = assignments;
        this.audit = audit;
        this.json = json;
        this.validityMinutes = props.routing() == null ? 60 : Math.max(1, props.routing().recommendationValidityMinutes());
    }

    // ------------------------------------------------------------------ eligibility only

    @Transactional
    public RoutingDtos.EligibilityResponse checkEligibility(Long claimId, String actor) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.notFound("Claim", claimId));
        requireRoutable(claim);
        Evaluation ev = evaluate(claim);
        RecommendationRun run = saveRun(claim, RecommendationRun.RunType.ELIGIBILITY_ONLY, ev, MlStatus.NOT_REQUESTED,
                null, null, null, ev.eligible().isEmpty() ? fallbackReason(claim, ev) : null, actor);
        saveEligibility(run, ev);
        audit.record(AuditEntry.of("ELIGIBILITY_CHECKED", "CLAIM", String.valueOf(claim.getId()), actor)
                .claim(claim.getClaimNumber()).eligibility(eligibilitySummary(ev)).action("ELIGIBILITY_CHECK")
                .detail("runId", run.getId()));
        return new RoutingDtos.EligibilityResponse(run.getId(), ClaimDtos.ClaimResponse.from(claim),
                run.getOutcome().name(), ev.eligible().size(), ev.ineligible().size(),
                rows(ev.eligible()), rows(ev.ineligible()), breakdown(ev.ineligible()), rulesApplied(ev.rules()),
                run.getCreatedAt());
    }

    // ------------------------------------------------------------------ recommendations

    @Transactional
    public RoutingDtos.RecommendationResponse recommend(Long claimId, String actor) {
        Claim claim = claims.findByIdForUpdate(claimId).orElseThrow(() -> ApiException.notFound("Claim", claimId));
        requireRoutable(claim);
        // any open decision based on older recommendations is now stale
        assignments.cancelPendingForClaim(claim.getId());

        Evaluation ev = evaluate(claim);
        if (ev.eligible().isEmpty()) {
            return fallback(claim, ev, actor);
        }

        SettingsService.ActiveWeights active = settings.activeWeights();
        List<HandlerSnapshot> eligibleHandlers = ev.eligible().stream().map(EligibilityDecision::handler).toList();
        SuitabilityScores predicted = active.weights().useMl()
                ? suitability.score(ev.claim(), eligibleHandlers)          // ONLY eligible candidates are sent
                : SuitabilityScores.unavailable("disabled");
        RankingResult ranking = scoring.rank(ev.claim(), eligibleHandlers, active.weights(),
                predicted.available() ? predicted.scores() : Map.of());
        MlStatus mlStatus = !active.weights().useMl() ? MlStatus.DISABLED
                : ranking.mlUsed() ? MlStatus.USED : MlStatus.UNAVAILABLE;

        RecommendationRun run = saveRun(claim, RecommendationRun.RunType.RECOMMENDATION, ev, mlStatus,
                mlStatus == MlStatus.USED ? predicted.modelVersion() : null, active.settingsId(),
                json.write(labelledWeights(ranking.effectiveWeights())), null, actor);
        saveEligibility(run, ev);
        for (ScoredCandidate c : ranking.candidates()) {
            recommendations.save(new AssignmentRecommendation(run.getId(), c.handler().id(), c.rank(), c.totalScore(),
                    c.mlScore(), json.write(factorDtos(c.factors())), c.fairnessAdjusted(), c.explanation()));
        }
        ScoredCandidate top = ranking.candidates().get(0);
        audit.record(AuditEntry.of("RECOMMENDATIONS_GENERATED", "CLAIM", String.valueOf(claim.getId()), actor)
                .claim(claim.getClaimNumber()).recommended(top.handler().code()).score(top.totalScore())
                .eligibility(eligibilitySummary(ev)).explanation(top.explanation()).action("GENERATE_RECOMMENDATIONS")
                .modelVersion(run.getModelVersion()).detail("runId", run.getId())
                .detail("scoringBasis", scoringBasis(mlStatus)));

        return new RoutingDtos.RecommendationResponse(run.getId(), ClaimDtos.ClaimResponse.from(claim),
                run.getOutcome().name(), scoringBasis(mlStatus), scoringNote(mlStatus),
                labelledWeights(ranking.effectiveWeights()),
                ranking.candidates().stream().map(c -> toDto(c, ev.claim())).toList(),
                ev.eligible().size(), ev.ineligible().size(), rows(ev.ineligible()), breakdown(ev.ineligible()),
                null, run.getCreatedAt().plusSeconds(validityMinutes * 60L), run.getCreatedAt());
    }

    private RoutingDtos.RecommendationResponse fallback(Claim claim, Evaluation ev, String actor) {
        String reason = fallbackReason(claim, ev);
        RecommendationRun run = saveRun(claim, RecommendationRun.RunType.RECOMMENDATION, ev, MlStatus.NOT_REQUESTED,
                null, null, null, reason, actor);
        saveEligibility(run, ev);
        RoutingDtos.FallbackDto fb;
        if (claim.getStatus() == ClaimStatus.UNASSIGNED) {
            Assignment a = new Assignment();
            a.setClaimId(claim.getId());
            a.setRunId(run.getId());
            a.setStatus(Assignment.Status.MANUAL_REVIEW);
            a.setDecisionType(Assignment.DecisionType.FALLBACK_MANUAL_REVIEW);
            a.setFallbackReason(reason);
            a.setMlStatus(MlStatus.NOT_REQUESTED);
            a.setCreatedBy(actor);
            a.setDecidedBy("system");
            a.setDecidedAt(Instant.now());
            assignments.save(a);
            claim.setStatus(ClaimStatus.MANUAL_REVIEW);
            history.record(claim, ClaimStatus.UNASSIGNED, ClaimStatus.MANUAL_REVIEW,
                    "No eligible handler - sent to manual review: " + reason, users.findByUsernameIgnoreCase(actor).orElse(null));
            audit.record(AuditEntry.of("FALLBACK_MANUAL_REVIEW", "ASSIGNMENT", String.valueOf(a.getId()), actor)
                    .claim(claim.getClaimNumber()).eligibility(eligibilitySummary(ev))
                    .action("FALLBACK_MANUAL_REVIEW").explanation(reason).detail("runId", run.getId()));
            fb = new RoutingDtos.FallbackDto(reason, ClaimStatus.MANUAL_REVIEW.name(), a.getId(), true);
        } else {
            audit.record(AuditEntry.of("ROUTING_NO_ELIGIBLE_HANDLER", "CLAIM", String.valueOf(claim.getId()), actor)
                    .claim(claim.getClaimNumber()).eligibility(eligibilitySummary(ev)).explanation(reason)
                    .detail("runId", run.getId()));
            fb = new RoutingDtos.FallbackDto(reason, claim.getStatus().name(), null, false);
        }
        return new RoutingDtos.RecommendationResponse(run.getId(), ClaimDtos.ClaimResponse.from(claim),
                run.getOutcome().name(), "NONE", "No handler satisfies every mandatory requirement, so no ranking was produced.",
                Map.of(), List.of(), 0, ev.ineligible().size(), rows(ev.ineligible()), breakdown(ev.ineligible()),
                fb, null, run.getCreatedAt());
    }

    // ------------------------------------------------------------------ history

    @Transactional(readOnly = true)
    public List<RoutingDtos.RunSummary> runsForClaim(Long claimId) {
        return runs.findTop20ByClaimIdOrderByCreatedAtDesc(claimId).stream()
                .map(r -> new RoutingDtos.RunSummary(r.getId(), r.getRunType().name(), r.getOutcome().name(),
                        r.getEligibleCount(), r.getIneligibleCount(),
                        r.getRunType() == RecommendationRun.RunType.RECOMMENDATION ? scoringBasis(r.getMlStatus()) : "NONE",
                        r.getCreatedBy(), r.getCreatedAt()))
                .toList();
    }

    /** Re-reads a stored run. Handler details reflect the CURRENT handler record; rule results are as evaluated. */
    @Transactional(readOnly = true)
    public RoutingDtos.RecommendationResponse getRun(Long runId) {
        RecommendationRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Recommendation run", runId));
        Claim claim = claims.findById(run.getClaimId()).orElseThrow(() -> ApiException.notFound("Claim", run.getClaimId()));
        List<EligibilityResultEntity> results = eligibilityResults.findByRunId(runId);
        Map<Long, Handler> byId = handlers.findAllById(results.stream().map(EligibilityResultEntity::getHandlerId).toList())
                .stream().collect(Collectors.toMap(Handler::getId, Function.identity()));
        List<RoutingDtos.EligibilityRow> ineligible = results.stream().filter(r -> !r.isEligible())
                .filter(r -> byId.containsKey(r.getHandlerId()))
                .map(r -> new RoutingDtos.EligibilityRow(RoutingDtos.HandlerSummary.from(byId.get(r.getHandlerId()).toSnapshot()),
                        false, json.read(r.getRuleResultsJson(), new TypeReference<List<RoutingDtos.RuleResultDto>>() {
                }), r.getExclusionReasons()))
                .sorted((a, b) -> a.handler().code().compareTo(b.handler().code()))
                .toList();
        ClaimSnapshot snapshot = claim.toSnapshot();
        List<RoutingDtos.RecommendationDto> recs = recommendations.findByRunIdOrderByRankAsc(runId).stream()
                .filter(r -> byId.containsKey(r.getHandlerId()))
                .map(r -> {
                    HandlerSnapshot h = byId.get(r.getHandlerId()).toSnapshot();
                    List<RoutingDtos.FactorDto> factors = json.read(r.getFactorScoresJson(), new TypeReference<List<RoutingDtos.FactorDto>>() {
                    });
                    double before = factors == null ? r.getTotalScore() : factors.stream().mapToDouble(RoutingDtos.FactorDto::points).sum();
                    return new RoutingDtos.RecommendationDto(r.getRank(), RoutingDtos.HandlerSummary.from(h), r.getTotalScore(),
                            Math.round(before * 100) / 100.0, r.isFairnessAdjusted(), factors, r.getExplanation(),
                            0, snapshot.location().equalsIgnoreCase(h.location()));
                }).toList();
        Map<String, Double> weights = run.getWeightsJson() == null ? Map.of()
                : json.read(run.getWeightsJson(), new TypeReference<Map<String, Double>>() {
        });
        boolean isRec = run.getRunType() == RecommendationRun.RunType.RECOMMENDATION;
        RoutingDtos.FallbackDto fb = run.getOutcome() == RecommendationRun.Outcome.NO_ELIGIBLE_HANDLER
                ? new RoutingDtos.FallbackDto(run.getFallbackReason(), claim.getStatus().name(), null, false) : null;
        return new RoutingDtos.RecommendationResponse(run.getId(), ClaimDtos.ClaimResponse.from(claim), run.getOutcome().name(),
                isRec && fb == null ? scoringBasis(run.getMlStatus()) : "NONE",
                isRec && fb == null ? scoringNote(run.getMlStatus()) : null, weights, recs, run.getEligibleCount(),
                run.getIneligibleCount(), ineligible, List.of(), fb,
                isRec && fb == null ? run.getCreatedAt().plusSeconds(validityMinutes * 60L) : null, run.getCreatedAt());
    }

    // ------------------------------------------------------------------ helpers

    record Evaluation(ClaimSnapshot claim, List<RuleConfig> rules, List<EligibilityDecision> decisions) {
        List<EligibilityDecision> eligible() {
            return decisions.stream().filter(EligibilityDecision::eligible).toList();
        }

        List<EligibilityDecision> ineligible() {
            return decisions.stream().filter(d -> !d.eligible()).toList();
        }
    }

    private Evaluation evaluate(Claim claim) {
        ClaimSnapshot snapshot = claim.toSnapshot();
        List<RuleConfig> rules = eligibility.effectiveRules(settings.activeRuleConfigs());
        List<HandlerSnapshot> all = handlers.findAll().stream().map(Handler::toSnapshot).toList();
        return new Evaluation(snapshot, rules, eligibility.evaluateAll(snapshot, all, rules));
    }

    private static void requireRoutable(Claim claim) {
        if (!claim.isRoutable()) {
            throw ApiException.conflict("Claim " + claim.getClaimNumber() + " is " + claim.getStatus()
                    + (claim.isTriaged() ? "" : " and has not been triaged yet")
                    + " and cannot be routed. Triage the claim first; only triaged claims that are unassigned or in manual review can be routed.");
        }
    }

    private RecommendationRun saveRun(Claim claim, RecommendationRun.RunType type, Evaluation ev, MlStatus mlStatus,
                                      String modelVersion, Long settingsId, String weightsJson, String fallbackReason,
                                      String actor) {
        RecommendationRun run = new RecommendationRun();
        run.setClaimId(claim.getId());
        run.setRunType(type);
        run.setOutcome(ev.eligible().isEmpty() ? RecommendationRun.Outcome.NO_ELIGIBLE_HANDLER : RecommendationRun.Outcome.ELIGIBLE_FOUND);
        run.setEligibleCount(ev.eligible().size());
        run.setIneligibleCount(ev.ineligible().size());
        run.setMlStatus(mlStatus);
        run.setModelVersion(modelVersion);
        run.setSettingsId(settingsId);
        run.setWeightsJson(weightsJson);
        run.setRulesJson(json.write(rulesApplied(ev.rules())));
        run.setFallbackReason(fallbackReason);
        run.setCreatedBy(actor);
        return runs.save(run);
    }

    private void saveEligibility(RecommendationRun run, Evaluation ev) {
        List<EligibilityResultEntity> rows = new ArrayList<>();
        for (EligibilityDecision d : ev.decisions()) {
            rows.add(new EligibilityResultEntity(run.getId(), d.handler().id(), d.eligible(),
                    json.write(ruleDtos(d.ruleResults())), d.exclusionSummary()));
        }
        eligibilityResults.saveAll(rows);
    }

    static String eligibilitySummary(Evaluation ev) {
        return ev.eligible().size() + " eligible, " + ev.ineligible().size() + " excluded";
    }

    /** Business-language explanation of why nobody is eligible, built from the actual rule failures. */
    static String fallbackReason(Claim claim, Evaluation ev) {
        Map<String, Long> counts = failureCounts(ev.ineligible());
        List<String> parts = new ArrayList<>();
        counts.forEach((code, n) -> parts.add(switch (code) {
            case RuleCode.ACTIVE -> n + " inactive";
            case RuleCode.AVAILABLE -> n + " unavailable";
            case RuleCode.EXPERTISE -> n + " without " + claim.getClaimType().label() + " expertise";
            case RuleCode.CAPACITY -> n + " with no workload capacity";
            case RuleCode.CRITICAL_SEVERITY_EXPERIENCE -> n + " below the critical-claim experience requirement";
            case RuleCode.HIGH_FRAUD_RISK_EXPERIENCE -> n + " below the high fraud-risk experience requirement";
            default -> n + " failing rule " + code;
        }));
        return "No handler meets every mandatory requirement for this " + claim.getClaimType().label() + " claim. Of "
                + ev.decisions().size() + " handlers: " + String.join("; ", parts)
                + ". The claim has been sent for manual review; eligibility rules were not relaxed.";
    }

    private static Map<String, Long> failureCounts(List<EligibilityDecision> ineligible) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EligibilityDecision d : ineligible) {
            for (RuleResult r : d.failedRules()) {
                counts.merge(r.code(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private static List<RoutingDtos.ExclusionCount> breakdown(List<EligibilityDecision> ineligible) {
        Map<String, String> names = new LinkedHashMap<>();
        ineligible.forEach(d -> d.failedRules().forEach(r -> names.putIfAbsent(r.code(), r.name())));
        return failureCounts(ineligible).entrySet().stream()
                .map(e -> new RoutingDtos.ExclusionCount(e.getKey(), names.get(e.getKey()), e.getValue()))
                .sorted((a, b) -> Long.compare(b.handlers(), a.handlers()))
                .toList();
    }

    private static List<RoutingDtos.EligibilityRow> rows(List<EligibilityDecision> decisions) {
        return decisions.stream().map(d -> new RoutingDtos.EligibilityRow(RoutingDtos.HandlerSummary.from(d.handler()),
                d.eligible(), ruleDtos(d.ruleResults()), d.exclusionSummary())).toList();
    }

    private static List<RoutingDtos.RuleResultDto> ruleDtos(List<RuleResult> results) {
        return results.stream().map(r -> new RoutingDtos.RuleResultDto(r.code(), r.name(), r.passed(), r.reason())).toList();
    }

    private static List<RoutingDtos.RuleAppliedDto> rulesApplied(List<RuleConfig> rules) {
        return rules.stream().map(r -> new RoutingDtos.RuleAppliedDto(r.code(), r.name(), r.param1(), r.param2())).toList();
    }

    static List<RoutingDtos.FactorDto> factorDtos(List<FactorScore> factors) {
        return factors.stream().map(f -> new RoutingDtos.FactorDto(f.factor().name(), f.label(), f.value(), f.weight(),
                f.points(), f.detail())).toList();
    }

    private static RoutingDtos.RecommendationDto toDto(ScoredCandidate c, ClaimSnapshot claim) {
        return new RoutingDtos.RecommendationDto(c.rank(), RoutingDtos.HandlerSummary.from(c.handler()), c.totalScore(),
                c.scoreBeforeFairness(), c.fairnessAdjusted(), factorDtos(c.factors()), c.explanation(),
                Math.round(c.distanceKm() * 10) / 10.0, claim.location().equalsIgnoreCase(c.handler().location()));
    }

    static Map<String, Double> labelledWeights(Map<Factor, Double> weights) {
        Map<String, Double> out = new LinkedHashMap<>();
        weights.forEach((f, w) -> out.put(f.label(), Math.round(w * 100) / 100.0));
        return out;
    }

    static String scoringBasis(MlStatus status) {
        return status == MlStatus.USED ? "PROFILE_AND_PREDICTED_FIT" : "PROFILE_ONLY";
    }

    static String scoringNote(MlStatus status) {
        return switch (status) {
            case USED -> "Suitability combines handler profile factors with predicted claim fit from past assignment outcomes.";
            case DISABLED -> "Suitability is based on handler profile factors. Predicted claim fit is switched off in Settings.";
            default -> "Predicted claim fit is temporarily unavailable, so suitability is based on handler profile factors only. "
                    + "Its weight has been shared proportionally across the other factors.";
        };
    }
}
