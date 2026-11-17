package com.smartassign.reference;

import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.Expertise;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.Severity;
import com.smartassign.location.CityDistanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Option lists for forms and filters, so the frontend never hard-codes enum values. */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final CityDistanceService cities;

    public ReferenceController(CityDistanceService cities) {
        this.cities = cities;
    }

    public record Option(String value, String label) {
    }

    public record Options(List<Option> claimTypes, List<Option> policyTypes, List<Option> severities,
                          List<Option> complexities, List<Option> incidentTypes, List<Option> expertise,
                          List<Option> claimStatuses, List<String> cities,
                          java.util.Map<String, List<String>> policyTypesByClaimType,
                          java.util.Map<String, List<String>> incidentTypesByClaimType) {
    }

    @GetMapping("/options")
    public Options options() {
        return new Options(
                Arrays.stream(ClaimType.values()).map(v -> new Option(v.name(), v.label())).toList(),
                Arrays.stream(PolicyType.values()).map(v -> new Option(v.name(), pretty(v.name()))).toList(),
                Arrays.stream(Severity.values()).map(v -> new Option(v.name(), pretty(v.name()))).toList(),
                Arrays.stream(Complexity.values()).map(v -> new Option(v.name(), pretty(v.name()))).toList(),
                Arrays.stream(IncidentType.values()).map(v -> new Option(v.name(), pretty(v.name()))).toList(),
                Arrays.stream(Expertise.values()).map(v -> new Option(v.name(), v.label())).toList(),
                Arrays.stream(ClaimStatus.values()).map(v -> new Option(v.name(), pretty(v.name()))).toList(),
                cities.knownCities(),
                byType(com.smartassign.claim.ClaimService.POLICY_TYPES),
                byType(com.smartassign.claim.ClaimService.INCIDENT_TYPES));
    }

    private static <E extends Enum<E>> java.util.Map<String, List<String>> byType(java.util.Map<ClaimType, java.util.Set<E>> m) {
        java.util.Map<String, List<String>> out = new java.util.TreeMap<>();
        m.forEach((k, v) -> out.put(k.name(), v.stream().map(Enum::name).sorted().toList()));
        return out;
    }

    static String pretty(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String w : s.split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}
