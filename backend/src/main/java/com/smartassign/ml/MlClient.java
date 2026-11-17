package com.smartassign.ml;

import com.smartassign.config.AppProperties;
import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;
import com.smartassign.core.ml.MlFeatureBuilder;
import com.smartassign.core.ml.SuitabilityScorer;
import com.smartassign.core.ml.SuitabilityScores;
import com.smartassign.core.scoring.DistanceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the internal suitability service for ELIGIBLE candidates only. Never throws: any failure (service down,
 * timeout, malformed or out-of-range output, missing candidates) yields "unavailable", and ranking then uses the
 * documented profile-only fallback. Scores are passed through unchanged - nothing is invented.
 */
@Component
public class MlClient implements SuitabilityScorer {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);

    private final AppProperties.Ml cfg;
    private final DistanceProvider distances;
    private final RestClient http;

    public MlClient(AppProperties props, DistanceProvider distances) {
        this.cfg = props.ml();
        this.distances = distances;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg == null ? 1000 : Math.max(100, cfg.connectTimeoutMs()));
        factory.setReadTimeout(cfg == null ? 3000 : Math.max(200, cfg.readTimeoutMs()));
        this.http = RestClient.builder()
                .baseUrl(cfg == null || cfg.baseUrl() == null ? "http://localhost:8000" : cfg.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public boolean enabled() {
        return cfg != null && cfg.enabled();
    }

    @Override
    public SuitabilityScores score(ClaimSnapshot claim, List<HandlerSnapshot> eligibleCandidates) {
        if (!enabled()) {
            return SuitabilityScores.unavailable("disabled");
        }
        if (eligibleCandidates.isEmpty()) {
            return SuitabilityScores.unavailable("no candidates");
        }
        try {
            List<MlContract.Candidate> candidates = eligibleCandidates.stream()
                    .map(h -> new MlContract.Candidate(String.valueOf(h.id()),
                            MlFeatureBuilder.build(claim, h, distances.distanceOrMax(claim.location(), h.location()))))
                    .toList();
            MlContract.PredictResponse res = http.post().uri("/ml/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MlContract.PredictRequest(candidates))
                    .retrieve()
                    .body(MlContract.PredictResponse.class);
            return validate(res, eligibleCandidates);
        } catch (Exception e) {
            log.warn("Suitability service unavailable for claim {}: {}", claim.claimNumber(), e.getMessage());
            return SuitabilityScores.unavailable("service error: " + e.getClass().getSimpleName());
        }
    }

    static SuitabilityScores validate(MlContract.PredictResponse res, List<HandlerSnapshot> candidates) {
        if (res == null || !"ok".equalsIgnoreCase(res.status()) || res.predictions() == null) {
            return SuitabilityScores.unavailable("invalid response" + (res == null || res.detail() == null ? "" : ": " + res.detail()));
        }
        Map<Long, Double> scores = new HashMap<>();
        for (MlContract.Prediction p : res.predictions()) {
            if (p.candidateId() == null || p.suitability() == null || p.suitability().isNaN()
                    || p.suitability() < 0.0 || p.suitability() > 1.0) {
                return SuitabilityScores.unavailable("prediction out of range for candidate " + p.candidateId());
            }
            try {
                scores.put(Long.parseLong(p.candidateId()), p.suitability());
            } catch (NumberFormatException e) {
                return SuitabilityScores.unavailable("unknown candidate id " + p.candidateId());
            }
        }
        for (HandlerSnapshot h : candidates) {
            if (!scores.containsKey(h.id())) {
                return SuitabilityScores.unavailable("missing prediction for candidate " + h.id());
            }
        }
        return new SuitabilityScores(true, scores, res.modelVersion(), null);
    }

    public Optional<MlContract.HealthResponse> health() {
        if (!enabled()) return Optional.empty();
        try {
            return Optional.ofNullable(http.get().uri("/ml/health").retrieve().body(MlContract.HealthResponse.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> modelInfo() {
        if (!enabled()) return Optional.empty();
        try {
            return Optional.ofNullable(http.get().uri("/ml/model-info").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    }));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
