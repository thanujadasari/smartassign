package com.smartassign.fairness;

import com.smartassign.config.AppProperties;
import com.smartassign.core.fairness.FairnessCalculator;
import com.smartassign.core.fairness.FairnessReport;
import com.smartassign.core.fairness.HandlerLoad;
import com.smartassign.handler.Handler;
import com.smartassign.handler.HandlerRepository;
import com.smartassign.settings.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FairnessService {

    private final HandlerRepository handlers;
    private final JdbcTemplate jdbc;
    private final FairnessCalculator calculator;
    private final SettingsService settings;
    private final int defaultWindow;

    public FairnessService(HandlerRepository handlers, JdbcTemplate jdbc, FairnessCalculator calculator,
                           SettingsService settings, AppProperties props) {
        this.handlers = handlers;
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.settings = settings;
        this.defaultWindow = props.routing() == null ? 30 : props.routing().fairnessWindowDays();
    }

    public record Bucket(String label, long handlers) {
    }

    public record FairnessSummary(int windowDays, Instant generatedAt, String note, FairnessReport report,
                                  List<Bucket> utilizationDistribution, Map<String, Long> assignmentsByExpertise,
                                  Map<String, Long> topRecommendationsByExpertise) {
    }

    @Transactional(readOnly = true)
    public FairnessSummary summary(Integer windowDays) {
        int window = windowDays == null || windowDays < 1 ? defaultWindow : Math.min(windowDays, 365);
        Timestamp since = Timestamp.from(Instant.now().minus(window, ChronoUnit.DAYS));

        Map<Long, Integer> assigned = new HashMap<>();
        jdbc.query("""
                SELECT assigned_handler_id, count(*) FROM assignments
                WHERE status IN ('APPROVED','OVERRIDDEN') AND decided_at >= ? AND assigned_handler_id IS NOT NULL
                GROUP BY assigned_handler_id""", rs -> {
            assigned.put(rs.getLong(1), rs.getInt(2));
        }, since);
        Map<Long, Integer> topRecs = new HashMap<>();
        jdbc.query("""
                SELECT ar.handler_id, count(*) FROM assignment_recommendations ar
                JOIN recommendation_runs r ON r.id = ar.run_id
                WHERE ar.rank = 1 AND r.created_at >= ? GROUP BY ar.handler_id""", rs -> {
            topRecs.put(rs.getLong(1), rs.getInt(2));
        }, since);

        List<Handler> all = handlers.findAllByOrderByCodeAsc();
        List<HandlerLoad> loads = all.stream()
                .map(h -> new HandlerLoad(h.toSnapshot(), assigned.getOrDefault(h.getId(), 0), topRecs.getOrDefault(h.getId(), 0)))
                .toList();
        double threshold = settings.activeWeights().weights().fairnessThreshold();
        FairnessReport report = calculator.compute(loads, threshold);

        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String b : List.of("0-25%", "25-50%", "50-75%", "75-99%", "Full", "Over capacity")) buckets.put(b, 0L);
        Map<String, Long> byExpertise = new LinkedHashMap<>();
        Map<String, Long> recsByExpertise = new LinkedHashMap<>();
        for (Handler h : all) {
            if (h.isActive()) {
                double u = h.utilization();
                String b = h.getCurrentWorkload() > h.getMaxWorkload() ? "Over capacity"
                        : h.getCurrentWorkload() == h.getMaxWorkload() ? "Full"
                        : u < 0.25 ? "0-25%" : u < 0.5 ? "25-50%" : u < 0.75 ? "50-75%" : "75-99%";
                buckets.merge(b, 1L, Long::sum);
            }
            String label = h.getPrimaryExpertise().label();
            byExpertise.merge(label, (long) assigned.getOrDefault(h.getId(), 0), Long::sum);
            recsByExpertise.merge(label, (long) topRecs.getOrDefault(h.getId(), 0), Long::sum);
        }
        return new FairnessSummary(window, Instant.now(),
                "Fairness is assessed relative to each handler's capacity within the same line of expertise. "
                        + "Warnings are indicators for supervisor review, not automatic findings of unfairness.",
                report, buckets.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue())).toList(),
                byExpertise, recsByExpertise);
    }
}
