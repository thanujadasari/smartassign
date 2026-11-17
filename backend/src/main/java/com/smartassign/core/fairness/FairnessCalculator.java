package com.smartassign.core.fairness;

import com.smartassign.core.HandlerSnapshot;
import com.smartassign.domain.Expertise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fairness here does NOT mean equal claim counts. Expected share is proportional to CAPACITY within the
 * same expertise group, and only active + available handlers are compared. Results are warnings for review.
 *
 * Indicators:
 *   OVER_CAPACITY      current workload above maximum                                          (critical)
 *   AT_CAPACITY        active, available handler with no open slot                             (info)
 *   HIGH_UTILIZATION   utilisation >= threshold                                                (warning)
 *   CONCENTRATION      assignments in window >= 5 and > 2x the capacity-proportional expectation (warning)
 *   GROUP_IMBALANCE    expertise group (>= 3 handlers) whose utilisation coefficient of variation > 0.5 (warning)
 */
public class FairnessCalculator {

    public static final double GROUP_CV_LIMIT = 0.5;
    public static final double CONCENTRATION_MULTIPLE = 2.0;
    public static final int CONCENTRATION_MIN_ASSIGNMENTS = 5;

    public FairnessReport compute(List<HandlerLoad> loads, double highUtilizationThreshold) {
        List<FairnessWarning> warnings = new ArrayList<>();
        List<HandlerLoad> pool = loads.stream().filter(l -> l.handler().active() && l.handler().available()).toList();

        Map<Expertise, List<HandlerLoad>> groups = pool.stream()
                .collect(Collectors.groupingBy(l -> l.handler().primaryExpertise()));
        Map<Expertise, Integer> groupAssignments = groups.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().mapToInt(HandlerLoad::assignmentsInWindow).sum()));
        Map<Expertise, Integer> groupCapacity = groups.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().mapToInt(l -> l.handler().maxWorkload()).sum()));

        List<FairnessReport.HandlerFairness> rows = new ArrayList<>();
        for (HandlerLoad l : loads.stream().sorted(Comparator.comparing(x -> x.handler().code())).toList()) {
            HandlerSnapshot h = l.handler();
            double util = h.utilization();
            boolean inPool = h.active() && h.available();
            double expected = 0;
            if (inPool) {
                int cap = groupCapacity.getOrDefault(h.primaryExpertise(), 0);
                expected = cap == 0 ? 0 : groupAssignments.getOrDefault(h.primaryExpertise(), 0) * (double) h.maxWorkload() / cap;
            }
            String status;
            if (!h.active()) {
                status = "INACTIVE";
            } else if (h.currentWorkload() > h.maxWorkload()) {
                status = "OVER_CAPACITY";
                warnings.add(new FairnessWarning("OVER_CAPACITY", "CRITICAL", h.code(), h.primaryExpertise().name(),
                        h.name() + " holds " + h.currentWorkload() + " claims against a maximum of " + h.maxWorkload() + "."));
            } else if (!h.available()) {
                status = "UNAVAILABLE";
            } else if (h.currentWorkload() == h.maxWorkload()) {
                status = "AT_CAPACITY";
                warnings.add(new FairnessWarning("AT_CAPACITY", "INFO", h.code(), h.primaryExpertise().name(),
                        h.name() + " has no open slots and will not receive new claims."));
            } else if (util >= highUtilizationThreshold) {
                status = "HIGH_UTILIZATION";
                warnings.add(new FairnessWarning("HIGH_UTILIZATION", "WARNING", h.code(), h.primaryExpertise().name(),
                        h.name() + " is at " + Math.round(util * 100) + "% of capacity."));
            } else {
                status = "BALANCED";
            }
            if (inPool && l.assignmentsInWindow() >= CONCENTRATION_MIN_ASSIGNMENTS
                    && l.assignmentsInWindow() > CONCENTRATION_MULTIPLE * expected) {
                warnings.add(new FairnessWarning("CONCENTRATION", "WARNING", h.code(), h.primaryExpertise().name(),
                        h.name() + " received " + l.assignmentsInWindow() + " recent assignments versus about "
                                + Math.round(expected) + " expected from their share of " + h.primaryExpertise().label()
                                + " capacity. Review whether this reflects expertise or location needs."));
            }
            rows.add(new FairnessReport.HandlerFairness(h.code(), h.name(), h.primaryExpertise().name(), h.location(),
                    h.active(), h.available(), h.currentWorkload(), h.maxWorkload(), round4(util),
                    l.assignmentsInWindow(), round2(expected), l.topRecommendationsInWindow(), status));
        }

        List<FairnessReport.GroupStats> groupStats = new ArrayList<>();
        for (Expertise e : Expertise.values()) {
            List<HandlerLoad> g = groups.getOrDefault(e, List.of());
            if (g.isEmpty()) continue;
            List<Double> utils = g.stream().map(l -> l.handler().utilization()).toList();
            double mean = Stats.mean(utils);
            double cv = mean == 0 ? 0 : Stats.stdDev(utils) / mean;
            groupStats.add(new FairnessReport.GroupStats(e.name(), e.label(), g.size(), groupCapacity.get(e),
                    g.stream().mapToInt(l -> l.handler().currentWorkload()).sum(), round4(mean), round4(cv),
                    groupAssignments.get(e)));
            if (g.size() >= 3 && cv > GROUP_CV_LIMIT) {
                warnings.add(new FairnessWarning("GROUP_IMBALANCE", "WARNING", null, e.name(),
                        "Workload across " + e.label() + " handlers is uneven (utilisation ranges from "
                                + Math.round(utils.stream().mapToDouble(d -> d).min().orElse(0) * 100) + "% to "
                                + Math.round(utils.stream().mapToDouble(d -> d).max().orElse(0) * 100) + "%)."));
            }
        }

        List<HandlerSnapshot> active = loads.stream().map(HandlerLoad::handler).filter(HandlerSnapshot::active).toList();
        List<Double> poolUtils = pool.stream().map(l -> l.handler().utilization()).toList();
        warnings.sort(Comparator.comparingInt((FairnessWarning w) -> switch (w.severity()) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        }).thenComparing(w -> w.handlerCode() == null ? "" : w.handlerCode()));

        return new FairnessReport(
                active.size(), pool.size(),
                round4(Stats.mean(poolUtils)), round4(Stats.stdDev(poolUtils)),
                round4(Stats.gini(pool.stream().map(HandlerLoad::assignmentsInWindow).toList())),
                (int) loads.stream().filter(l -> l.handler().currentWorkload() > l.handler().maxWorkload()).count(),
                (int) rows.stream().filter(r -> r.status().equals("AT_CAPACITY")).count(),
                (int) rows.stream().filter(r -> r.status().equals("HIGH_UTILIZATION")).count(),
                highUtilizationThreshold, groupStats, rows, warnings);
    }

    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
