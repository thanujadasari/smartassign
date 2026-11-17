package com.smartassign.core.triage;

import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.Priority;
import com.smartassign.domain.Severity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Suggests severity, complexity and fraud risk for a newly registered claim. The supervisor always makes the
 * final decision - these are recommendations with their reasons, never applied automatically.
 *
 * How each suggestion is grounded (see documentation/triage-rules.md):
 *  SEVERITY   thresholds on the estimated loss, fitted to the training dataset (7,000 claims).
 *             In that data these cut-points reproduce the recorded severity exactly for 59% of claims and
 *             to within one level for 99%. Injury and incident type did not change severity in the data.
 *  COMPLEXITY the loss is only weakly related to complexity in the data, so the loss thresholds are
 *             combined with ASSUMED business rules (injury, third party, liability lines).
 *  FRAUD RISK the dataset's fraud score is not related to any field a customer enters, so it cannot be
 *             learned from that data. The suggestion is an ASSUMED red-flag checklist; every flag is listed.
 *  PRIORITY   ASSUMED business rules (the dataset has no priority column):
 *             HIGH   injury involved, fire, major property damage, ongoing risk / further damage reported,
 *                    or a very large loss - cases needing immediate attention
 *             MEDIUM moderate vehicle accident or property damage, or a claim needing investigation
 *                    (theft, fraud red flags, liability with a third party) without immediate danger
 *             LOW    minor vehicle or property damage that needs no immediate intervention
 */
public final class TriageAdvisor {

    // severity cut-points on estimated loss (INR): LOW < 115k <= MEDIUM < 308k <= HIGH < 834k <= CRITICAL
    public static final double SEVERITY_MEDIUM_FROM = 115_000;
    public static final double SEVERITY_HIGH_FROM = 308_000;
    public static final double SEVERITY_CRITICAL_FROM = 834_000;

    // complexity cut-points on estimated loss (INR): LOW < 122k <= MEDIUM < 834k <= HIGH
    public static final double COMPLEXITY_MEDIUM_FROM = 122_000;
    public static final double COMPLEXITY_HIGH_FROM = 834_000;

    /** 95th percentile of estimated loss per claim type in the training dataset (INR). */
    public static final Map<ClaimType, Double> LOSS_P95 = Map.of(
            ClaimType.AUTO, 606_000d,
            ClaimType.LIABILITY, 841_000d,
            ClaimType.PROPERTY, 1_067_000d,
            ClaimType.WORKERS_COMPENSATION, 844_000d);

    public static final double FRAUD_BASE = 0.10;
    public static final int LATE_REPORT_DAYS = 30;

    public record Input(ClaimType claimType, IncidentType incidentType, double estimatedLoss, LocalDate incidentDate,
                        LocalDate reportedOn, boolean injuryInvolved, boolean thirdPartyInvolved, String description,
                        int otherClaimsByCustomerLast12Months, boolean ongoingRisk) {
    }

    public record Reason(String text, String basis) {
    }

    public record FraudFlag(String code, String text, double weight) {
    }

    public record Suggestion(Severity severity, List<Reason> severityReasons,
                             Complexity complexity, List<Reason> complexityReasons,
                             double fraudRiskScore, List<FraudFlag> fraudFlags, String fraudNote,
                             Priority priority, List<Reason> priorityReasons) {
    }

    public static final String BASIS_DATA = "DATA";        // derived from the training dataset
    public static final String BASIS_ASSUMED = "ASSUMED";  // assumed business rule - adjust to your policy

    public Suggestion suggest(Input in) {
        // ---------- severity
        List<Reason> sr = new ArrayList<>();
        Severity severity;
        if (in.estimatedLoss() >= SEVERITY_CRITICAL_FROM) severity = Severity.CRITICAL;
        else if (in.estimatedLoss() >= SEVERITY_HIGH_FROM) severity = Severity.HIGH;
        else if (in.estimatedLoss() >= SEVERITY_MEDIUM_FROM) severity = Severity.MEDIUM;
        else severity = Severity.LOW;
        sr.add(new Reason("Estimated loss of " + inr(in.estimatedLoss()) + " falls in the " + severity
                + " band (" + band(severity) + ")", BASIS_DATA));

        // ---------- complexity
        List<Reason> cr = new ArrayList<>();
        Complexity complexity;
        if (in.estimatedLoss() >= COMPLEXITY_HIGH_FROM) complexity = Complexity.HIGH;
        else if (in.estimatedLoss() >= COMPLEXITY_MEDIUM_FROM) complexity = Complexity.MEDIUM;
        else complexity = Complexity.LOW;
        cr.add(new Reason("Loss amount suggests " + complexity + " complexity", BASIS_DATA));
        if (in.injuryInvolved() && in.thirdPartyInvolved() && complexity == Complexity.LOW) {
            complexity = Complexity.MEDIUM;
            cr.add(new Reason("Injury with a third party involved raised it to at least MEDIUM", BASIS_ASSUMED));
        }
        if (in.claimType() == ClaimType.LIABILITY && in.injuryInvolved() && complexity != Complexity.HIGH
                && in.estimatedLoss() >= SEVERITY_HIGH_FROM) {
            complexity = Complexity.HIGH;
            cr.add(new Reason("Liability claim with injury and a large loss raised it to HIGH", BASIS_ASSUMED));
        }

        // ---------- fraud red flags
        List<FraudFlag> flags = new ArrayList<>();
        if (in.incidentDate() != null && in.reportedOn() != null) {
            long late = ChronoUnit.DAYS.between(in.incidentDate(), in.reportedOn());
            if (late > LATE_REPORT_DAYS) {
                flags.add(new FraudFlag("LATE_REPORT", "Reported " + late + " days after the incident", 0.15));
            }
        }
        Double p95 = LOSS_P95.get(in.claimType());
        if (p95 != null && in.estimatedLoss() > p95) {
            flags.add(new FraudFlag("UNUSUALLY_HIGH_LOSS", "Loss is higher than 95% of " + in.claimType().label()
                    + " claims (" + inr(p95) + ")", 0.15));
        }
        if (in.incidentType() == IncidentType.THEFT) {
            flags.add(new FraudFlag("THEFT", "Theft claims are reviewed more often for fraud", 0.10));
        }
        if (in.otherClaimsByCustomerLast12Months() >= 2) {
            flags.add(new FraudFlag("FREQUENT_CLAIMANT", "Customer registered " + in.otherClaimsByCustomerLast12Months()
                    + " other claims in the last 12 months", 0.15));
        }
        String desc = in.description() == null ? "" : in.description().trim();
        if (desc.length() < 40) {
            flags.add(new FraudFlag("THIN_DESCRIPTION", "Very short incident description", 0.05));
        }
        double score = FRAUD_BASE + flags.stream().mapToDouble(FraudFlag::weight).sum();
        score = Math.round(Math.min(0.95, score) * 1000.0) / 1000.0;
        String note = "Red-flag checklist (assumed rules). Start at " + FRAUD_BASE + " and add the weight of each flag. "
                + "The supervisor decides the final score.";

        // ---------- priority
        List<Reason> pr = new ArrayList<>();
        double loss = in.estimatedLoss();
        boolean property = in.claimType() == ClaimType.PROPERTY;
        boolean vehicle = in.claimType() == ClaimType.AUTO;
        if (in.injuryInvolved()) pr.add(new Reason("Someone was injured", BASIS_ASSUMED));
        if (in.incidentType() == IncidentType.FIRE) pr.add(new Reason("Fire", BASIS_ASSUMED));
        if (property && loss >= SEVERITY_HIGH_FROM) pr.add(new Reason("Major property damage (" + inr(loss) + ")", BASIS_ASSUMED));
        if (in.ongoingRisk()) pr.add(new Reason("Customer reports ongoing risk or further damage", BASIS_ASSUMED));
        if (loss >= SEVERITY_CRITICAL_FROM && !property) pr.add(new Reason("Very large loss (" + inr(loss) + ") needs immediate attention", BASIS_ASSUMED));
        Priority priority;
        if (!pr.isEmpty()) {
            priority = Priority.HIGH;
        } else {
            if (vehicle && loss >= SEVERITY_MEDIUM_FROM) pr.add(new Reason("Moderate vehicle accident or damage (" + inr(loss) + ")", BASIS_ASSUMED));
            if (property && loss >= SEVERITY_MEDIUM_FROM) pr.add(new Reason("Moderate property damage (" + inr(loss) + ")", BASIS_ASSUMED));
            if (!vehicle && !property && loss >= SEVERITY_MEDIUM_FROM) pr.add(new Reason("Moderate loss (" + inr(loss) + ")", BASIS_ASSUMED));
            if (in.incidentType() == IncidentType.THEFT) pr.add(new Reason("Theft needs investigation", BASIS_ASSUMED));
            if (flags.stream().anyMatch(f -> !"THEFT".equals(f.code()))) pr.add(new Reason("Fraud red flags need investigation", BASIS_ASSUMED));
            if (in.claimType() == ClaimType.LIABILITY && in.thirdPartyInvolved()) pr.add(new Reason("Third-party liability needs investigation", BASIS_ASSUMED));
            if (!pr.isEmpty()) {
                priority = Priority.MEDIUM;
            } else {
                priority = Priority.LOW;
                pr.add(new Reason(vehicle ? "Minor vehicle damage (" + inr(loss) + "), no injury"
                        : property ? "Small property damage (" + inr(loss) + ")"
                        : "Minor claim (" + inr(loss) + "), no injury or ongoing risk", BASIS_ASSUMED));
            }
        }

        return new Suggestion(severity, List.copyOf(sr), complexity, List.copyOf(cr), score, List.copyOf(flags), note,
                priority, List.copyOf(pr));
    }

    private static String band(Severity s) {
        return switch (s) {
            case LOW -> "below " + inr(SEVERITY_MEDIUM_FROM);
            case MEDIUM -> inr(SEVERITY_MEDIUM_FROM) + " to " + inr(SEVERITY_HIGH_FROM);
            case HIGH -> inr(SEVERITY_HIGH_FROM) + " to " + inr(SEVERITY_CRITICAL_FROM);
            case CRITICAL -> inr(SEVERITY_CRITICAL_FROM) + " and above";
        };
    }

    static String inr(double v) {
        return "INR " + String.format(java.util.Locale.ROOT, "%,.0f", v);
    }
}
