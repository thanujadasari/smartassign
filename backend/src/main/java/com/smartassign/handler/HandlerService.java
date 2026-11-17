package com.smartassign.handler;

import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.common.ApiException;
import com.smartassign.common.PageResponse;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import com.smartassign.location.CityDistanceService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Supervisor-side handler management. Handlers are created by staff sign-up, never by hand. */
@Service
public class HandlerService {

    private final HandlerRepository repo;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final CityDistanceService cities;

    public HandlerService(HandlerRepository repo, AuditService audit, JdbcTemplate jdbc, CityDistanceService cities) {
        this.repo = repo;
        this.audit = audit;
        this.jdbc = jdbc;
        this.cities = cities;
    }

    public record HandlerFilter(String q, Expertise expertise, String location, Boolean available, Boolean active,
                                String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<HandlerDtos.HandlerResponse> list(HandlerFilter f, Pageable pageable) {
        return PageResponse.of(repo.findAll(spec(f), pageable), HandlerDtos.HandlerResponse::from);
    }

    @Transactional(readOnly = true)
    public HandlerDtos.HandlerDetail detail(Long id) {
        Handler h = find(id);
        Long total = jdbc.queryForObject("SELECT count(*) FROM assignments WHERE assigned_handler_id = ? AND status IN ('APPROVED','OVERRIDDEN')", Long.class, id);
        Long overrides = jdbc.queryForObject("SELECT count(*) FROM assignments WHERE assigned_handler_id = ? AND status = 'OVERRIDDEN'", Long.class, id);
        Long top = jdbc.queryForObject("SELECT count(*) FROM assignment_recommendations WHERE handler_id = ? AND rank = 1", Long.class, id);
        return new HandlerDtos.HandlerDetail(HandlerDtos.HandlerResponse.from(h), claims(id, "ASSIGNED"),
                claims(id, "RESOLVED"), availabilityHistory(id),
                total == null ? 0 : total, overrides == null ? 0 : overrides, top == null ? 0 : top);
    }

    public List<HandlerDtos.AssignedClaim> claims(Long handlerId, String status) {
        return jdbc.query("""
                        SELECT c.id, c.claim_number, c.claim_type, c.severity, c.claim_location, c.status,
                               u.full_name, c.assigned_at, c.resolved_at
                        FROM claims c LEFT JOIN users u ON u.id = c.assigned_by_user_id
                        WHERE c.assigned_handler_id = ? AND c.status = ?
                        ORDER BY COALESCE(c.resolved_at, c.assigned_at) DESC NULLS LAST LIMIT 100""",
                (rs, i) -> new HandlerDtos.AssignedClaim(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant()),
                handlerId, status);
    }

    public List<HandlerDtos.AvailabilityEvent> availabilityHistory(Long handlerId) {
        return jdbc.query("""
                        SELECT available, reason, changed_by, changed_at FROM handler_availability_log
                        WHERE handler_id = ? ORDER BY changed_at DESC, id DESC LIMIT 50""",
                (rs, i) -> new HandlerDtos.AvailabilityEvent(rs.getBoolean(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()),
                handlerId);
    }

    @Transactional(readOnly = true)
    public HandlerDtos.HandlerResponse get(Long id) {
        return HandlerDtos.HandlerResponse.from(find(id));
    }

    @Transactional
    public HandlerDtos.HandlerResponse update(Long id, HandlerDtos.HandlerUpdateRequest req, String actor) {
        Handler h = repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Handler", id));
        if (!cities.isKnownCity(req.location())) {
            throw ApiException.badRequest("Location must be one of the supported cities");
        }
        String before = summary(h);
        h.setPrimaryExpertise(req.primaryExpertise());
        Set<ClaimType> skills = EnumSet.noneOf(ClaimType.class);
        skills.addAll(req.primaryExpertise().impliedSkills());
        if (req.additionalSkills() != null) skills.addAll(req.additionalSkills());
        h.setSkills(skills);
        h.setExperienceYears(req.experienceYears());
        h.setMaxWorkload(req.maxWorkload());
        h.setLocation(cities.canonical(req.location()));
        audit.record(AuditEntry.of("HANDLER_UPDATED", "HANDLER", h.getCode(), actor).finalHandler(h.getCode())
                .detail("before", before).detail("after", summary(h)));
        return HandlerDtos.HandlerResponse.from(h);
    }

    @Transactional
    public HandlerDtos.HandlerResponse setActive(Long id, HandlerDtos.StatusRequest req, String actor) {
        Handler h = repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Handler", id));
        boolean changed = h.isActive() != req.active();
        h.setActive(req.active());
        if (changed) {
            audit.record(AuditEntry.of("HANDLER_STATUS_CHANGED", "HANDLER", h.getCode(), actor).finalHandler(h.getCode())
                    .details((req.active() ? "Activated" : "Deactivated")
                            + (req.reason() == null || req.reason().isBlank() ? "" : ": " + req.reason())));
        }
        return HandlerDtos.HandlerResponse.from(h);
    }

    /** Used by the handler portal: the handler sets their own availability. */
    @Transactional
    public HandlerDtos.HandlerResponse setAvailability(Long id, HandlerDtos.AvailabilityRequest req, String actor) {
        Handler h = repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Handler", id));
        String reason = req.reason() == null ? null : req.reason().trim();
        if (!req.available() && (reason == null || reason.length() < 5)) {
            throw ApiException.rule("Please give a reason for being unavailable (at least 5 characters)");
        }
        boolean changed = h.isAvailable() != req.available()
                || (!req.available() && !reason.equals(h.getUnavailableReason()));
        if (!changed) {
            return HandlerDtos.HandlerResponse.from(h);
        }
        h.setAvailable(req.available());
        h.setUnavailableReason(req.available() ? null : reason);
        h.setAvailabilityChangedAt(Instant.now());
        jdbc.update("INSERT INTO handler_availability_log (handler_id, available, reason, changed_by) VALUES (?,?,?,?)",
                h.getId(), req.available(), req.available() ? null : reason, actor);
        audit.record(AuditEntry.of("HANDLER_AVAILABILITY_CHANGED", "HANDLER", h.getCode(), actor).finalHandler(h.getCode())
                .details(req.available() ? "Marked available" : "Marked unavailable: " + reason));
        return HandlerDtos.HandlerResponse.from(h);
    }

    Handler find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Handler", id));
    }

    private static String summary(Handler h) {
        return h.getPrimaryExpertise() + ", " + h.getLocation() + ", capacity " + h.getMaxWorkload()
                + ", experience=" + h.getExperienceYears() + ", skills=" + h.getSkills();
    }

    static Specification<Handler> spec(HandlerFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (f.expertise() != null) p.add(cb.equal(root.get("primaryExpertise"), f.expertise()));
            if (f.location() != null && !f.location().isBlank()) p.add(cb.equal(cb.lower(root.get("location")), f.location().trim().toLowerCase()));
            if (f.available() != null) p.add(cb.equal(root.get("available"), f.available()));
            if (f.active() != null) p.add(cb.equal(root.get("active"), f.active()));
            if (f.status() != null && !f.status().isBlank()) {
                switch (f.status().toUpperCase()) {
                    case "INACTIVE" -> p.add(cb.isFalse(root.get("active")));
                    case "UNAVAILABLE" -> p.add(cb.and(cb.isTrue(root.get("active")), cb.isFalse(root.get("available"))));
                    case "AT_CAPACITY" -> p.add(cb.and(cb.isTrue(root.get("active")), cb.isTrue(root.get("available")),
                            cb.equal(root.get("currentWorkload"), root.get("maxWorkload"))));
                    case "OVER_CAPACITY" -> p.add(cb.and(cb.isTrue(root.get("active")),
                            cb.greaterThan(root.<Integer>get("currentWorkload"), root.<Integer>get("maxWorkload"))));
                    case "AVAILABLE" -> p.add(cb.and(cb.isTrue(root.get("active")), cb.isTrue(root.get("available")),
                            cb.lessThan(root.<Integer>get("currentWorkload"), root.<Integer>get("maxWorkload"))));
                    default -> throw ApiException.badRequest("Unknown handler status filter: " + f.status());
                }
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
