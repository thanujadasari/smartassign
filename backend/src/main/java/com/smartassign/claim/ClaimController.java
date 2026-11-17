package com.smartassign.claim;

import com.smartassign.audit.AuditDto;
import com.smartassign.audit.AuditService;
import com.smartassign.auth.AuthService;
import com.smartassign.common.PageResponse;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Priority;
import com.smartassign.domain.Severity;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Supervisor view of every claim registered by customers (URL security: SUPERVISOR only). */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private static final Set<String> SORTABLE = Set.of("claimNumber", "claimType", "severity", "complexity",
            "estimatedLoss", "location", "fraudRiskScore", "status", "createdAt", "updatedAt", "assignedAt", "incidentDate",
            "priorityRank");

    private final ClaimService claims;
    private final AuditService audit;
    private final AuthService auth;

    public ClaimController(ClaimService claims, AuditService audit, AuthService auth) {
        this.claims = claims;
        this.audit = audit;
        this.auth = auth;
    }

    @GetMapping
    public PageResponse<ClaimDtos.ClaimResponse> list(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) ClaimStatus status,
                                                      @RequestParam(required = false) ClaimType claimType,
                                                      @RequestParam(required = false) Severity severity,
                                                      @RequestParam(required = false) String location,
                                                      @RequestParam(required = false) Long handlerId,
                                                      @RequestParam(required = false) Priority priority,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "25") int size,
                                                      @RequestParam(defaultValue = "createdAt") String sort,
                                                      @RequestParam(defaultValue = "desc") String direction) {
        // "priority" sorts most urgent first (HIGH, MEDIUM, LOW), using the decided or suggested priority
        String field = "priority".equals(sort) ? "priorityRank" : SORTABLE.contains(sort) ? sort : "createdAt";
        Sort s = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, field)
                .and(Sort.by("id"));
        return claims.list(new ClaimService.ClaimFilter(q, status, claimType, severity, location, handlerId, priority),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), s));
    }

    @GetMapping("/{id}")
    public ClaimDtos.ClaimDetail get(@PathVariable Long id) {
        return claims.detail(id);
    }

    @GetMapping("/{id}/activity")
    public List<AuditDto> activity(@PathVariable Long id) {
        return audit.forClaim(claims.get(id).claimNumber());
    }

    @GetMapping("/{id}/triage-suggestion")
    public ClaimDtos.TriageSuggestion triageSuggestion(@PathVariable Long id) {
        return claims.suggestion(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/priority")
    public ClaimDtos.ClaimDetail priority(@PathVariable Long id, @Valid @RequestBody ClaimDtos.PriorityRequest req) {
        return claims.changePriority(id, req, auth.currentUser());
    }

    @PostMapping("/{id}/triage")
    public ClaimDtos.ClaimDetail triage(@PathVariable Long id, @Valid @RequestBody ClaimDtos.TriageRequest req) {
        return claims.triage(id, req, auth.currentUser());
    }
}
