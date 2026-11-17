package com.smartassign.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Wire contract with the internal suitability service (ml-service, FastAPI). snake_case on the wire.
 *
 *   POST /ml/predict   {"candidates":[{"candidate_id":"42","features":{...24 features...}}]}
 *                  ->  {"status":"ok","model_version":"...","predictions":[{"candidate_id":"42","suitability":0.83}]}
 *   GET  /ml/health ->  {"status":"ok","model_loaded":true,"model_version":"..."}
 *   GET  /ml/model-info -> internal metadata (never forwarded to the supervisor UI)
 */
public final class MlContract {

    private MlContract() {
    }

    public record Candidate(@JsonProperty("candidate_id") String candidateId, Map<String, Object> features) {
    }

    public record PredictRequest(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(@JsonProperty("candidate_id") String candidateId, Double suitability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictResponse(String status, @JsonProperty("model_version") String modelVersion,
                                  List<Prediction> predictions, String detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HealthResponse(String status, @JsonProperty("model_loaded") Boolean modelLoaded,
                                 @JsonProperty("model_version") String modelVersion) {
    }
}
