package com.smartassign.dashboard;

import com.smartassign.audit.AuditDto;
import com.smartassign.audit.AuditRepository;
import com.smartassign.claim.ClaimDtos;
import com.smartassign.claim.ClaimRepository;
import com.smartassign.config.AppProperties;
import com.smartassign.ml.SuitabilityServiceStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final List<String> ACTION_EVENTS = List.of("ASSIGNMENT_APPROVED", "ASSIGNMENT_OVERRIDDEN",
            "MANUAL_REVIEW_REQUESTED", "FALLBACK_MANUAL_REVIEW", "RECOMMENDATIONS_GENERATED", "CLAIM_SUBMITTED",
            "CLAIM_TRIAGED", "CLAIM_RETRIAGED", "CLAIM_RESOLVED", "HANDLER_AVAILABILITY_CHANGED", "HANDLER_STATUS_CHANGED", "SETTINGS_WEIGHTS_UPDATED",
            "CLAIM_PRIORITY_CHANGED");

    private final JdbcTemplate jdbc;
    private final ClaimRepository claims;
    private final AuditRepository audit;
    private final SuitabilityServiceStatus suitability;
    private final AppProperties props;

    public DashboardService(JdbcTemplate jdbc, ClaimRepository claims, AuditRepository audit,
                            SuitabilityServiceStatus suitability, AppProperties props) {
        this.jdbc = jdbc;
        this.claims = claims;
        this.audit = audit;
        this.suitability = suitability;
        this.props = props;
    }

    public record Kpis(long totalClaims, long awaitingTriage, long readyToAssign, long assignedClaims,
                       long manualReviewClaims, long resolvedClaims, long availableHandlers, long activeHandlers,
                       long unavailableHandlers, Double eligibleAssignmentRate, double averageHandlerUtilization,
                       long assignmentsLast7Days, long recommendationRunsLast30Days) {
    }

    public record LabelValue(String label, long value) {
    }

    public record TrendPoint(LocalDate date, long approved, long overridden, long manualReview) {
    }

    public record SystemStatus(String claimCenter, String suitabilityEngine, String database) {
    }

    public record UnavailableHandler(Long id, String code, String fullName, String reason, java.time.Instant since) {
    }

    public record Summary(Kpis kpis, List<LabelValue> claimsByStatus, List<LabelValue> claimsByType,
                          List<LabelValue> workloadDistribution, List<TrendPoint> assignmentTrend,
                          List<ClaimDtos.ClaimResponse> recentClaims, List<AuditDto> recentActions,
                          List<UnavailableHandler> unavailableHandlers, SystemStatus system) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("SUBMITTED", "UNASSIGNED", "ASSIGNED", "MANUAL_REVIEW", "RESOLVED")) byStatus.put(s, 0L);
        jdbc.query("SELECT status, count(*) FROM claims GROUP BY status", rs -> {
            byStatus.put(rs.getString(1), rs.getLong(2));
        });
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        List<LabelValue> byType = jdbc.query("SELECT claim_type, count(*) FROM claims GROUP BY claim_type ORDER BY 2 DESC",
                (rs, i) -> new LabelValue(rs.getString(1), rs.getLong(2)));

        Map<String, Object> h = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE active) AS active_handlers,
                       count(*) FILTER (WHERE active AND available AND current_workload < max_workload) AS available_handlers,
                       count(*) FILTER (WHERE active AND NOT available) AS unavailable_handlers,
                       COALESCE(avg(current_workload::numeric / NULLIF(max_workload, 0)) FILTER (WHERE active), 0) AS avg_util
                FROM handlers""");

        List<LabelValue> workload = jdbc.query("""
                SELECT bucket, count(*) FROM (
                  SELECT CASE WHEN current_workload > max_workload THEN '6:Over capacity'
                              WHEN current_workload = max_workload THEN '5:Full'
                              WHEN current_workload::numeric / max_workload < 0.25 THEN '1:0-25%'
                              WHEN current_workload::numeric / max_workload < 0.50 THEN '2:25-50%'
                              WHEN current_workload::numeric / max_workload < 0.75 THEN '3:50-75%'
                              ELSE '4:75-99%' END AS bucket
                  FROM handlers WHERE active) b
                GROUP BY bucket ORDER BY bucket""", (rs, i) -> new LabelValue(rs.getString(1).substring(2), rs.getLong(2)));

        Map<String, Object> runs = jdbc.queryForMap("""
                SELECT count(*) AS total, count(*) FILTER (WHERE outcome = 'ELIGIBLE_FOUND') AS found
                FROM recommendation_runs WHERE run_type = 'RECOMMENDATION' AND created_at >= now() - interval '30 days'""");
        long runTotal = ((Number) runs.get("total")).longValue();
        Double eligibleRate = runTotal == 0 ? null
                : Math.round(((Number) runs.get("found")).doubleValue() / runTotal * 10000.0) / 10000.0;

        Long last7 = jdbc.queryForObject("""
                SELECT count(*) FROM assignments WHERE status IN ('APPROVED','OVERRIDDEN','MANUAL_REVIEW')
                AND decided_at >= now() - interval '7 days'""", Long.class);

        List<TrendPoint> trend = jdbc.query("""
                SELECT d::date,
                       count(a.id) FILTER (WHERE a.status = 'APPROVED'),
                       count(a.id) FILTER (WHERE a.status = 'OVERRIDDEN'),
                       count(a.id) FILTER (WHERE a.status = 'MANUAL_REVIEW')
                FROM generate_series(current_date - 13, current_date, interval '1 day') d
                LEFT JOIN assignments a ON a.decided_at::date = d::date
                GROUP BY d ORDER BY d""",
                (rs, i) -> new TrendPoint(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), rs.getLong(4)));

        List<ClaimDtos.ClaimResponse> recent = claims.findAll(PageRequest.of(0, 8,
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(ClaimDtos.ClaimResponse::from).getContent();
        List<AuditDto> actions = audit.findTop10ByEventTypeInOrderByCreatedAtDesc(ACTION_EVENTS).stream().map(AuditDto::from).toList();

        List<UnavailableHandler> away = jdbc.query("""
                SELECT id, handler_code, full_name, unavailable_reason, availability_changed_at FROM handlers
                WHERE active AND NOT available ORDER BY availability_changed_at DESC NULLS LAST LIMIT 10""",
                (rs, i) -> new UnavailableHandler(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()));
        boolean ccEnabled = props.claimcenter() != null && props.claimcenter().enabled();

        Kpis kpis = new Kpis(total, byStatus.get("SUBMITTED"), byStatus.get("UNASSIGNED"), byStatus.get("ASSIGNED"),
                byStatus.get("MANUAL_REVIEW"), byStatus.get("RESOLVED"),
                ((Number) h.get("available_handlers")).longValue(), ((Number) h.get("active_handlers")).longValue(),
                ((Number) h.get("unavailable_handlers")).longValue(), eligibleRate,
                Math.round(((Number) h.get("avg_util")).doubleValue() * 10000.0) / 10000.0,
                last7 == null ? 0 : last7, runTotal);
        return new Summary(kpis,
                byStatus.entrySet().stream().map(e -> new LabelValue(e.getKey(), e.getValue())).toList(),
                byType, workload, trend, recent, actions, away,
                new SystemStatus(ccEnabled ? "CONFIGURED_NOT_VERIFIED" : "NOT_CONNECTED",
                        suitability.state().name(), "ONLINE"));
    }
}
