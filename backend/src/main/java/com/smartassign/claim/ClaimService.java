package com.smartassign.claim;

import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.auth.Role;
import com.smartassign.auth.User;
import com.smartassign.common.ApiException;
import com.smartassign.common.JsonSupport;
import com.smartassign.common.PageResponse;
import com.smartassign.core.triage.TriageAdvisor;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.Priority;
import com.smartassign.domain.RecordSource;
import com.smartassign.domain.Severity;
import com.smartassign.location.CityDistanceService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ClaimService {

    /** Which policy and incident types belong to each line of business (as in the training dataset). */
    public static final Map<ClaimType, Set<PolicyType>> POLICY_TYPES = Map.of(
            ClaimType.AUTO, EnumSet.of(PolicyType.AUTO),
            ClaimType.PROPERTY, EnumSet.of(PolicyType.HOME, PolicyType.COMMERCIAL_PROPERTY),
            ClaimType.LIABILITY, EnumSet.of(PolicyType.GENERAL_LIABILITY, PolicyType.COMMERCIAL_LIABILITY),
            ClaimType.WORKERS_COMPENSATION, EnumSet.of(PolicyType.WORKERS_COMPENSATION));
    public static final Map<ClaimType, Set<IncidentType>> INCIDENT_TYPES = Map.of(
            ClaimType.AUTO, EnumSet.of(IncidentType.COLLISION, IncidentType.DAMAGE, IncidentType.THEFT),
            ClaimType.PROPERTY, EnumSet.of(IncidentType.DAMAGE, IncidentType.FIRE, IncidentType.FLOOD, IncidentType.STORM, IncidentType.THEFT),
            ClaimType.LIABILITY, EnumSet.of(IncidentType.ACCIDENT, IncidentType.DAMAGE, IncidentType.LIABILITY),
            ClaimType.WORKERS_COMPENSATION, EnumSet.of(IncidentType.WORKPLACE_ACCIDENT, IncidentType.INJURY));

    private final ClaimRepository repo;
    private final ClaimStatusEventRepository events;
    private final ClaimHistory history;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final CityDistanceService cities;
    private final JsonSupport json;
    private final TriageAdvisor advisor = new TriageAdvisor();

    public ClaimService(ClaimRepository repo, ClaimStatusEventRepository events, ClaimHistory history,
                        AuditService audit, JdbcTemplate jdbc, CityDistanceService cities, JsonSupport json) {
        this.repo = repo;
        this.events = events;
        this.history = history;
        this.audit = audit;
        this.jdbc = jdbc;
        this.cities = cities;
        this.json = json;
    }

    public record ClaimFilter(String q, ClaimStatus status, ClaimType claimType, Severity severity, String location,
                              Long handlerId, Priority priority) {
    }

    // =====================================================================
    // Customer
    // =====================================================================

    @Transactional
    public ClaimDtos.CustomerClaimDetail submit(User customer, ClaimDtos.CustomerClaimRequest req) {
        if (customer.getRole() != Role.CUSTOMER) {
            throw ApiException.forbidden("Only customers can register claims");
        }
        if (!POLICY_TYPES.get(req.claimType()).contains(req.policyType())) {
            throw ApiException.badRequest("Policy type " + req.policyType() + " does not match a " + req.claimType().label() + " claim");
        }
        if (!INCIDENT_TYPES.get(req.claimType()).contains(req.incidentType())) {
            throw ApiException.badRequest("Incident type " + req.incidentType() + " does not match a " + req.claimType().label() + " claim");
        }
        if (!cities.isKnownCity(req.location())) {
            throw ApiException.badRequest("Location must be one of the supported cities");
        }
        if (req.incidentDate().isBefore(LocalDate.now(ZoneOffset.UTC).minusYears(3))) {
            throw ApiException.badRequest("Incidents older than 3 years cannot be registered online");
        }
        Claim c = new Claim();
        c.setClaimNumber(String.format("CLM-%06d", repo.nextClaimNumber()));
        c.setCustomer(customer);
        c.setClaimType(req.claimType());
        c.setPolicyType(req.policyType());
        c.setPolicyNumber(req.policyNumber().trim().toUpperCase());
        c.setIncidentType(req.incidentType());
        c.setIncidentDate(req.incidentDate());
        c.setLocation(cities.canonical(req.location()));
        c.setEstimatedLoss(req.estimatedLoss().setScale(2, RoundingMode.HALF_UP));
        c.setInjuryInvolved(req.injuryInvolved());
        c.setThirdPartyInvolved(req.thirdPartyInvolved());
        c.setOngoingRisk(Boolean.TRUE.equals(req.ongoingRisk()));
        c.setDescription(req.description().trim());
        c.setContactPhone(req.contactPhone() == null || req.contactPhone().isBlank() ? customer.getPhone() : req.contactPhone().trim());
        c.setStatus(ClaimStatus.SUBMITTED);
        c.setSource(RecordSource.CUSTOMER_PORTAL);
        repo.save(c);
        // the priority suggestion is stored at once so the triage queue can show the most urgent claims first
        TriageAdvisor.Suggestion s = computeSuggestion(c);
        c.setSuggestedPriority(s.priority());
        c.setPriorityReasonsJson(json.write(s.priorityReasons()));
        history.record(c, null, ClaimStatus.SUBMITTED, "Claim registered by the customer", customer);
        audit.record(AuditEntry.of("CLAIM_SUBMITTED", "CLAIM", String.valueOf(c.getId()), customer.getUsername())
                .claim(c.getClaimNumber())
                .details(c.getClaimType().label() + " claim, " + c.getIncidentType() + ", " + c.getLocation()));
        return customerDetail(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimDtos.CustomerClaim> customerClaims(User customer, Pageable pageable) {
        return PageResponse.of(repo.findByCustomerId(customer.getId(), pageable), ClaimDtos.CustomerClaim::from);
    }

    @Transactional(readOnly = true)
    public ClaimDtos.CustomerClaimDetail customerClaim(User customer, Long id) {
        Claim c = find(id);
        if (!c.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.notFound("Claim", id);   // never reveal other customers' claims
        }
        return customerDetail(c);
    }

    /** Customer timeline: internal routing steps are summarised and internal notes are not shown. */
    private ClaimDtos.CustomerClaimDetail customerDetail(Claim c) {
        List<ClaimDtos.StatusEvent> timeline = new ArrayList<>();
        String last = null;
        for (ClaimStatusEvent e : events.findByClaimIdOrderByCreatedAtAscIdAsc(c.getId())) {
            String to = ClaimDtos.CustomerClaim.customerStatus(ClaimStatus.valueOf(e.getToStatus()));
            if (to.equals(last)) continue;
            String note = switch (to) {
                case "SUBMITTED" -> "We received your claim";
                case "IN_REVIEW" -> "Our team is reviewing your claim";
                case "ASSIGNED" -> "A claim handler has been assigned";
                case "RESOLVED" -> e.getNote();
                default -> null;
            };
            String actor = "ASSIGNED".equals(to) || "RESOLVED".equals(to) || "SUBMITTED".equals(to) ? e.getActorName() : "Claims team";
            timeline.add(new ClaimDtos.StatusEvent(last, to, note, actor, "SUBMITTED".equals(to) ? "CUSTOMER" : "STAFF",
                    e.getCreatedAt()));
            last = to;
        }
        return new ClaimDtos.CustomerClaimDetail(ClaimDtos.CustomerClaim.from(c), timeline);
    }

    // =====================================================================
    // Supervisor
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<ClaimDtos.ClaimResponse> list(ClaimFilter f, Pageable pageable) {
        return PageResponse.of(repo.findAll(spec(f), pageable), ClaimDtos.ClaimResponse::from);
    }

    @Transactional(readOnly = true)
    public ClaimDtos.ClaimResponse get(Long id) {
        return ClaimDtos.ClaimResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public ClaimDtos.ClaimDetail detail(Long id) {
        Claim c = find(id);
        return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), timeline(id));
    }

    public List<ClaimDtos.StatusEvent> timeline(Long claimId) {
        return events.findByClaimIdOrderByCreatedAtAscIdAsc(claimId).stream().map(ClaimDtos.StatusEvent::of).toList();
    }

    /** Rule-based recommendation shown to the supervisor; nothing is saved. */
    @Transactional(readOnly = true)
    public ClaimDtos.TriageSuggestion suggestion(Long id) {
        return ClaimDtos.TriageSuggestion.of(computeSuggestion(find(id)));
    }

    private TriageAdvisor.Suggestion computeSuggestion(Claim c) {
        Integer others = jdbc.queryForObject("""
                SELECT count(*) FROM claims WHERE customer_id = ? AND id <> ? AND created_at >= now() - interval '12 months'""",
                Integer.class, c.getCustomer().getId(), c.getId());
        LocalDate reported = c.getCreatedAt() == null ? LocalDate.now(ZoneOffset.UTC) : c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return advisor.suggest(new TriageAdvisor.Input(c.getClaimType(), c.getIncidentType(), c.getEstimatedLoss().doubleValue(),
                c.getIncidentDate(), reported, c.isInjuryInvolved(), c.isThirdPartyInvolved(), c.getDescription(),
                others == null ? 0 : others, c.isOngoingRisk()));
    }

    /**
     * The supervisor's decision on severity, complexity and fraud risk. The rule suggestion at that moment is stored
     * next to the decision, so the audit shows where the supervisor agreed or departed from it.
     */
    @Transactional
    public ClaimDtos.ClaimDetail triage(Long id, ClaimDtos.TriageRequest req, User supervisor) {
        Claim c = repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Claim", id));
        if (c.getStatus() == ClaimStatus.ASSIGNED || c.getStatus() == ClaimStatus.RESOLVED) {
            throw ApiException.conflict("Claim " + c.getClaimNumber() + " is " + c.getStatus() + " and can no longer be triaged");
        }
        TriageAdvisor.Suggestion s = computeSuggestion(c);
        boolean departed = s.priority() != req.priority() || s.severity() != req.severity() || s.complexity() != req.complexity()
                || Math.abs(s.fraudRiskScore() - req.fraudRiskScore().doubleValue()) > 0.0005;
        if (departed && (req.note() == null || req.note().trim().length() < 10)) {
            throw ApiException.rule("Please add a note (at least 10 characters) explaining why your decision differs from the suggestion");
        }
        ClaimStatus from = c.getStatus();
        c.setSuggestedPriority(s.priority());
        c.setPriorityReasonsJson(json.write(s.priorityReasons()));
        c.setPriority(req.priority());
        c.setPriorityNote(s.priority() == req.priority() ? null : (req.note() == null ? null : req.note().trim()));
        c.setPrioritySetBy(supervisor);
        c.setPrioritySetAt(Instant.now());
        c.setSuggestedSeverity(s.severity());
        c.setSuggestedComplexity(s.complexity());
        c.setSuggestedFraudRisk(java.math.BigDecimal.valueOf(s.fraudRiskScore()));
        c.setTriageSuggestionJson(json.write(s));
        c.setSeverity(req.severity());
        c.setComplexity(req.complexity());
        c.setFraudRiskScore(req.fraudRiskScore().setScale(3, RoundingMode.HALF_UP));
        c.setTriageNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        c.setTriagedBy(supervisor);
        c.setTriagedAt(Instant.now());
        if (from == ClaimStatus.SUBMITTED) {
            c.setStatus(ClaimStatus.UNASSIGNED);
        }
        String summary = "Priority " + req.priority() + ", severity " + req.severity() + ", complexity " + req.complexity() + ", fraud risk "
                + c.getFraudRiskScore() + (departed ? " (differs from suggestion " + s.priority() + "/" + s.severity() + "/" + s.complexity()
                + "/" + s.fraudRiskScore() + ")" : " (as suggested)");
        history.record(c, from, c.getStatus(), "Triaged: " + summary, supervisor);
        audit.record(AuditEntry.of(from == ClaimStatus.SUBMITTED ? "CLAIM_TRIAGED" : "CLAIM_RETRIAGED", "CLAIM",
                        String.valueOf(c.getId()), supervisor.getUsername())
                .claim(c.getClaimNumber()).action(departed ? "TRIAGE_ADJUSTED" : "TRIAGE_ACCEPTED")
                .detail("suggested", Map.of("priority", s.priority(), "severity", s.severity(), "complexity", s.complexity(), "fraudRisk", s.fraudRiskScore()))
                .detail("decided", Map.of("priority", req.priority(), "severity", req.severity(), "complexity", req.complexity(), "fraudRisk", c.getFraudRiskScore()))
                .details(c.getTriageNote() == null ? summary : summary + ". Note: " + c.getTriageNote()));
        return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), timeline(c.getId()));
    }

    /** The supervisor may change the priority at any time before the claim is resolved; a reason is required. */
    @Transactional
    public ClaimDtos.ClaimDetail changePriority(Long id, ClaimDtos.PriorityRequest req, User supervisor) {
        Claim c = repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Claim", id));
        if (c.getStatus() == ClaimStatus.RESOLVED) {
            throw ApiException.conflict("Claim " + c.getClaimNumber() + " is resolved; its priority can no longer change");
        }
        if (c.getStatus() == ClaimStatus.SUBMITTED) {
            throw ApiException.conflict("Set the priority while triaging claim " + c.getClaimNumber());
        }
        Priority before = c.getPriority();
        if (before == req.priority()) {
            return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), timeline(c.getId()));
        }
        c.setPriority(req.priority());
        c.setPriorityNote(req.reason().trim());
        c.setPrioritySetBy(supervisor);
        c.setPrioritySetAt(Instant.now());
        String text = "Priority changed from " + before + " to " + req.priority() + ": " + req.reason().trim();
        history.record(c, c.getStatus(), c.getStatus(), text, supervisor);
        audit.record(AuditEntry.of("CLAIM_PRIORITY_CHANGED", "CLAIM", String.valueOf(c.getId()), supervisor.getUsername())
                .claim(c.getClaimNumber()).detail("from", String.valueOf(before)).detail("to", req.priority().name())
                .details(text));
        return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), timeline(c.getId()));
    }

    Claim find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Claim", id));
    }

    static Specification<Claim> spec(ClaimFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toUpperCase() + "%";
                var customer = root.join("customer", JoinType.LEFT);
                p.add(cb.or(cb.like(cb.upper(root.get("claimNumber")), like),
                        cb.like(cb.upper(root.get("policyNumber")), like),
                        cb.like(cb.upper(customer.get("fullName")), like)));
            }
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.claimType() != null) p.add(cb.equal(root.get("claimType"), f.claimType()));
            if (f.severity() != null) p.add(cb.equal(root.get("severity"), f.severity()));
            if (f.location() != null && !f.location().isBlank()) p.add(cb.equal(cb.lower(root.get("location")), f.location().trim().toLowerCase()));
            if (f.priority() != null) p.add(cb.equal(root.get("priority"), f.priority()));
            if (f.handlerId() != null) p.add(cb.equal(root.get("assignedHandler").get("id"), f.handlerId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    static long daysBetween(Instant a, Instant b) {
        return ChronoUnit.DAYS.between(a, b);
    }
}
