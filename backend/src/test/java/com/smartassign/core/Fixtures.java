package com.smartassign.core;

import com.smartassign.core.eligibility.RuleConfig;
import com.smartassign.core.scoring.DistanceProvider;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.Expertise;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.Severity;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Small, readable test data builders. */
public final class Fixtures {

    private Fixtures() {
    }

    public static ClaimSnapshot claim(ClaimType type, Severity severity, String city, double fraud) {
        PolicyType policy = switch (type) {
            case AUTO -> PolicyType.AUTO;
            case PROPERTY -> PolicyType.HOME;
            case LIABILITY -> PolicyType.GENERAL_LIABILITY;
            case WORKERS_COMPENSATION -> PolicyType.WORKERS_COMPENSATION;
        };
        return new ClaimSnapshot(1L, "C-TEST", type, policy, severity, Complexity.MEDIUM, 250_000, city, 5,
                IncidentType.DAMAGE, fraud, false, false);
    }

    public static ClaimSnapshot claim(ClaimType type) {
        return claim(type, Severity.MEDIUM, "Pune", 0.2);
    }

    public static HandlerSnapshot handler(long id, Expertise expertise, int current, int max) {
        return new HandlerSnapshot(id, String.format("A%03d", id), "Handler " + id, expertise, null, 10, current, max,
                true, true, "Pune", 0.75, 9.0, 50);
    }

    public static HandlerSnapshot handler(long id, Expertise expertise, int current, int max, boolean available,
                                          boolean active, int years, String city) {
        return new HandlerSnapshot(id, String.format("A%03d", id), "Handler " + id, expertise, null, years, current, max,
                available, active, city, 0.75, 9.0, 50);
    }

    public static List<RuleConfig> defaultRules() {
        return List.of(
                new RuleConfig("ACTIVE", "Handler is active", true, true, null, null),
                new RuleConfig("AVAILABLE", "Handler is available", true, true, null, null),
                new RuleConfig("EXPERTISE", "Required expertise", true, true, null, null),
                new RuleConfig("CAPACITY", "Workload capacity", true, true, null, null),
                new RuleConfig("CRITICAL_SEVERITY_EXPERIENCE", "Critical claims need senior handlers", true, false, 5.0, null),
                new RuleConfig("HIGH_FRAUD_RISK_EXPERIENCE", "High fraud-risk claims need experienced handlers", true, false, 0.7, 8.0));
    }

    public static DistanceProvider distances() {
        Map<String, Double> d = Map.of("Pune|Pune", 1.0, "Pune|Mumbai", 150.0, "Mumbai|Pune", 150.0,
                "Pune|Delhi", 1450.0, "Delhi|Pune", 1450.0, "Mumbai|Mumbai", 1.0, "Delhi|Delhi", 1.0);
        return new DistanceProvider() {
            @Override
            public OptionalDouble distanceKm(String from, String to) {
                Double v = d.get(from + "|" + to);
                return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
            }

            @Override
            public double maxDistanceKm() {
                return 2300;
            }
        };
    }
}
