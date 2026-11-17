package com.smartassign.audit;

import com.smartassign.common.CsvWriter;
import com.smartassign.common.JsonSupport;
import com.smartassign.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

    public static final int EXPORT_LIMIT = 50_000;

    private final AuditRepository repo;
    private final JsonSupport json;

    public AuditService(AuditRepository repo, JsonSupport json) {
        this.repo = repo;
        this.json = json;
    }

    /** Joins the caller's transaction so business change and audit record commit (or roll back) together. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEntry entry) {
        repo.save(new AuditLog(entry, entry.details.isEmpty() ? null : json.write(entry.details)));
    }

    public record AuditFilter(String claimNumber, String handlerCode, String eventType, String action, String actor,
                              Instant from, Instant to, String q) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDto> search(AuditFilter f, Pageable pageable) {
        Page<AuditLog> page = repo.findAll(spec(f), pageable);
        return PageResponse.of(page, AuditDto::from);
    }

    @Transactional(readOnly = true)
    public String exportCsv(AuditFilter f) {
        List<AuditLog> rows = repo.findAll(spec(f),
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        CsvWriter csv = new CsvWriter().row(List.of("id", "timestamp", "event_type", "claim_number",
                "recommended_handler", "final_handler", "recommendation_score", "supervisor_action", "override_reason",
                "eligibility_summary", "explanation", "actor", "entity_type", "entity_id"));
        for (AuditLog a : rows) {
            csv.row(java.util.Arrays.asList(a.getId(), a.getCreatedAt(), a.getEventType(), a.getClaimNumber(),
                    a.getRecommendedHandlerCode(), a.getFinalHandlerCode(), a.getRecommendationScore(),
                    a.getSupervisorAction(), a.getOverrideReason(), a.getEligibilitySummary(), a.getExplanation(),
                    a.getActorUsername(), a.getEntityType(), a.getEntityId()));
        }
        return csv.build();
    }

    @Transactional(readOnly = true)
    public List<AuditDto> forClaim(String claimNumber) {
        return repo.findByClaimNumberOrderByCreatedAtDesc(claimNumber).stream().map(AuditDto::from).toList();
    }

    static Specification<AuditLog> spec(AuditFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(f.claimNumber())) p.add(cb.equal(cb.upper(root.get("claimNumber")), f.claimNumber().trim().toUpperCase()));
            if (notBlank(f.handlerCode())) {
                String h = f.handlerCode().trim().toUpperCase();
                p.add(cb.or(cb.equal(root.get("recommendedHandlerCode"), h), cb.equal(root.get("finalHandlerCode"), h)));
            }
            if (notBlank(f.eventType())) p.add(cb.equal(root.get("eventType"), f.eventType().trim().toUpperCase()));
            if (notBlank(f.action())) p.add(cb.equal(root.get("supervisorAction"), f.action().trim().toUpperCase()));
            if (notBlank(f.actor())) p.add(cb.equal(cb.lower(root.get("actorUsername")), f.actor().trim().toLowerCase()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) p.add(cb.lessThan(root.get("createdAt"), f.to()));
            if (notBlank(f.q())) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("claimNumber")), like),
                        cb.like(cb.lower(root.get("explanation")), like),
                        cb.like(cb.lower(root.get("overrideReason")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
